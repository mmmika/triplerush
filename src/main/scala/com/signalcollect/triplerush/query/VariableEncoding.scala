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

package com.signalcollect.triplerush.query

import com.signalcollect.triplerush.TriplePattern

object VariableEncoding {

  @inline def variableIdToDecodingIndex(variableId: Int) = -(variableId + 1)

  @inline def requiredVariableBindingsSlots(query: Seq[TriplePattern]): Int = {
    var minId = 0
    query.foreach {
      tp =>
        minId = math.min(minId, tp.s)
        minId = math.min(minId, tp.p)
        minId = math.min(minId, tp.o)
    }
    -minId
  }

}
