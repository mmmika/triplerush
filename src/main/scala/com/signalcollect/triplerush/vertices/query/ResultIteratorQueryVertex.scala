/*
 *  @author Philip Stutz
 *
 *  Copyright 2014 University of Zurich
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.signalcollect.triplerush.vertices.query

import com.signalcollect.GraphEditor
import com.signalcollect.triplerush.{ OperationIds, TriplePattern }
import com.signalcollect.triplerush.dictionary.RdfDictionary
import com.signalcollect.triplerush.util.ResultIterator

import akka.event.LoggingAdapter

class ResultIteratorQueryVertex(
  query: Seq[TriplePattern],
  numberOfSelectVariables: Int,
  tickets: Long,
  resultIterator: ResultIterator,
  dictionary: RdfDictionary,
  log: LoggingAdapter)
    extends AbstractQueryVertex[ResultIterator](query, tickets, numberOfSelectVariables, dictionary, log) {

  final val id = OperationIds.embedInLong(OperationIds.nextId)

  override final def afterInitialization(graphEditor: GraphEditor[Long, Any]): Unit = {
    state = resultIterator
    super.afterInitialization(graphEditor)
  }

  def handleBindings(bindings: Array[Array[Int]]): Unit = {
    state.add(bindings)
  }

  def handleResultCount(resultCount: Long): Unit = {
    throw new UnsupportedOperationException("Result binding vertex should never receive a result count.")
  }

  override final def reportResults(complete: Boolean): Unit = {
    // Empty array implicitly signals that there are no more results.
    state.add(Array[Array[Int]]())
    if (log.isDebugEnabled) {
      val endTime = System.nanoTime
      val deltaNanoseconds = endTime - startTime
      val deltaMilliseconds = (deltaNanoseconds / 100000.0).round / 10.0
      log.debug(s"""
| Query execution report:
|   query = ${query.map(_.toDecodedString(dictionary)).mkString("[", ",\n", "]")}
|   execution time = $deltaMilliseconds milliseconds
|   number of results = $resultCount
|   approximated size of explored search space = $approximationOfExploredSearchSpace
""".stripMargin)
    }
  }

}
