/*
 * Copyright 2018-2019 Fs2 Redis
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

package com.github.gvolpe

import cats.effect.Sync
import io.chrisdavenport.log4cats.Logger

package object fs2redis {

  private[fs2redis] def putStrLn[F[_]: Sync, A](a: A): F[Unit] =
    Sync[F].delay(println(a))

  implicit def testLogger[F[_]: Sync]: Logger[F] =
    new Logger[F] {
      def debug(t: Throwable)(message: => String): F[Unit] = debug(message)
      def error(t: Throwable)(message: => String): F[Unit] = error(message)
      def info(t: Throwable)(message: => String): F[Unit]  = info(message)
      def trace(t: Throwable)(message: => String): F[Unit] = trace(message)
      def warn(t: Throwable)(message: => String): F[Unit]  = warn(message)

      def debug(message: => String): F[Unit] = putStrLn(message)
      def error(message: => String): F[Unit] = putStrLn(message)
      def info(message: => String): F[Unit]  = putStrLn(message)
      def trace(message: => String): F[Unit] = putStrLn(message)
      def warn(message: => String): F[Unit]  = putStrLn(message)
    }

}
