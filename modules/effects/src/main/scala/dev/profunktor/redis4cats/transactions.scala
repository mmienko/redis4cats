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

import cats.effect._
import cats.effect.concurrent._
import cats.effect.implicits._
import cats.implicits._
import dev.profunktor.redis4cats.algebra._
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.hlist._
import dev.profunktor.redis4cats.JavaConversions._
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

object transactions {

  case object TransactionAborted extends NoStackTrace

  case class RedisTransaction[F[_]: Concurrent: Log: Timer, K, V](
      cmd: RedisCommands[F, K, V]
  ) {

    /***
      * Exclusively run Redis commands as part of a transaction.
      *
      * Every command needs to be forked (`.start`) to be sent to the server asynchronously.
      * After a transaction is complete, either successfully or with a failure, the spawned
      * fibers will be treated accordingly.
      *
      * It should not be used to run other computations, only Redis commands. Fail to do so
      * may end in unexpected results such as a dead lock.
      */
    def exec[T <: HList, R <: HList](commands: T)(implicit w: Witness.Aux[T, R]): F[R] =
      Deferred[F, Either[Throwable, w.R]].flatMap { promise =>
        // Runs every command in order, discarding the enqueing result
        def runner[H <: HList](ys: H): F[Unit] =
          ys match {
            case HNil                           => F.unit
            case HCons((h: F[_] @unchecked), t) => h.void >> runner(t)
          }

        // FIXME: atm we have no way to know what's the type we should return.
        // Sometimes we may get a Long, so should we return Long or Option[Long]?
        // That behavior is very specific to each command. Same with the ones that
        // return something like Option[FiniteDuration]
        def toHList(list: List[Any]): HList =
          list match {
            case Nil => HNil
            case (x :: xs) =>
              if (x.isInstanceOf[String])
                Option(x) :: toHList(xs)
              else
                x :: toHList(xs)
          }

        val tx =
          Resource.makeCase(cmd.multi) {
            case (_, ExitCase.Completed) =>
              for {
                _ <- F.info("Transaction completed")
                tr <- cmd.exec.map(_.iterator().asScala.toList)
                // Casting here is fine since we have a `Witness` that proves this true
                res = toHList(tr).asInstanceOf[w.R]
                _ <- promise.complete(res.asRight)
              } yield ()
            case (_, ExitCase.Error(e)) =>
              F.error(s"Transaction failed: ${e.getMessage}") >>
                  cmd.discard >> promise.complete(TransactionAborted.asLeft)
            case (_, ExitCase.Canceled) =>
              F.error("Transaction canceled") >>
                  cmd.discard >> promise.complete(TransactionAborted.asLeft)
            case _ =>
              F.error("Kernel panic: the impossible happened!")
          }

        F.info("Transaction started") >>
          (tx.use(_ => runner(commands)) >> promise.get.rethrow).timeout(3.seconds)
      }

  }

}
