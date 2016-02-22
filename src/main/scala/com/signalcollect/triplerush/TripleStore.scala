/*
 * Copyright (C) 2015 Cotiviti Labs (nexgen.admin@cotiviti.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.signalcollect.triplerush

import scala.concurrent.Future
import akka.NotUsed
import akka.stream.scaladsl.Source
import com.signalcollect.triplerush.query.VariableEncoding

object TripleStore {

  type Variable = Int
  type Binding = Int

}

trait TripleStore {

  def addTriplePatterns(triplePatterns: Source[TriplePattern, NotUsed]): Future[NotUsed]

  def query(query: Vector[TriplePattern]): Source[Bindings, NotUsed]

  def close(): Unit

}
