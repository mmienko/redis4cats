/*
 * Copyright 2018-2020 ProfunKtor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.profunktor.redis4cats
package streams

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import dev.profunktor.redis4cats.connection._
import dev.profunktor.redis4cats.data._
import dev.profunktor.redis4cats.effect.{ JRFuture, Log }
import dev.profunktor.redis4cats.effect.JRFuture._
import dev.profunktor.redis4cats.streams.data._
import fs2.Stream
import io.lettuce.core.{ ReadFrom => JReadFrom }

object RedisStream {

  def mkStreamingConnection[F[_]: Concurrent: ContextShift: Log, K, V](
      client: RedisClient,
      codec: RedisCodec[K, V]
  ): Stream[F, Streaming[Stream[F, *], K, V]] =
    Stream.resource(mkStreamingConnectionResource(client, codec))

  def mkStreamingConnectionResource[F[_]: Concurrent: ContextShift: Log, K, V](
      client: RedisClient,
      codec: RedisCodec[K, V]
  ): Resource[F, Streaming[Stream[F, *], K, V]] =
    mkBlocker[F].flatMap { blocker =>
      val acquire = JRFuture
        .fromConnectionFuture(F.delay(client.underlying.connectAsync[K, V](codec.underlying, client.uri.underlying)))(
          blocker
        )
        .map(new RedisRawStreaming(_, blocker))

      val release: RedisRawStreaming[F, K, V] => F[Unit] = c =>
        JRFuture.fromCompletableFuture(F.delay(c.client.closeAsync()))(blocker) *>
            F.info(s"Releasing Streaming connection: ${client.uri.underlying}")

      Resource.make(acquire)(release).map(rs => new RedisStream(rs))
    }

  def mkMasterReplicaConnection[F[_]: Concurrent: ContextShift: Log, K, V](
      codec: RedisCodec[K, V],
      uris: RedisURI*
  )(readFrom: Option[JReadFrom] = None): Stream[F, Streaming[Stream[F, *], K, V]] =
    Stream.resource(mkMasterReplicaConnectionResource(codec, uris: _*)(readFrom))

  def mkMasterReplicaConnectionResource[F[_]: Concurrent: ContextShift: Log, K, V](
      codec: RedisCodec[K, V],
      uris: RedisURI*
  )(readFrom: Option[JReadFrom] = None): Resource[F, Streaming[Stream[F, *], K, V]] =
    mkBlocker[F].flatMap { blocker =>
      RedisMasterReplica[F].make(codec, uris: _*)(readFrom).map { conn =>
        new RedisStream(new RedisRawStreaming(conn.underlying, blocker))
      }
    }

}

class RedisStream[F[_]: Concurrent, K, V](rawStreaming: RedisRawStreaming[F, K, V])
    extends Streaming[Stream[F, *], K, V] {

  private[streams] val nextOffset: K => XReadMessage[K, V] => StreamingOffset[K] =
    key =>
      msg =>
        StreamingOffset.Custom(
          key,
          // increment the sequence number (second part of the ID after the dash)
          s"${msg.id.value.takeWhile(_ != '-')}-${msg.id.value.dropWhile(_ != '-').drop(1).toLong + 1}"
        )

  private[streams] val offsetsByKey: List[XReadMessage[K, V]] => Map[K, Option[StreamingOffset[K]]] =
    list => list.groupBy(_.key).map { case (k, values) => k -> values.lastOption.map(nextOffset(k)) }

  override def append: Stream[F, XAddMessage[K, V]] => Stream[F, MessageId] =
    _.evalMap(msg => rawStreaming.xAdd(msg.key, msg.body, msg.approxMaxlen))

  override def read(keys: Set[K], initialOffset: K => StreamingOffset[K]): Stream[F, XReadMessage[K, V]] = {
    val initial = keys.map(k => k -> initialOffset(k)).toMap
    Stream.eval(Ref.of[F, Map[K, StreamingOffset[K]]](initial)).flatMap { ref =>
      (for {
        offsets <- Stream.eval(ref.get)
        list <- Stream.eval(rawStreaming.xRead(offsets.values.toSet))
        newOffsets = offsetsByKey(list).collect { case (key, Some(value)) => key -> value }.toList
        _ <- Stream.eval(newOffsets.map { case (k, v) => ref.update(_.updated(k, v)) }.sequence)
        result <- Stream.fromIterator(list.iterator)
      } yield result).repeat
    }
  }

}