/*
 *  @author Philip Stutz
 *  @author Mihaela Verman
 *
 *  Copyright 2013 University of Zurich
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

package com.signalcollect.triplerush.vertices

import com.signalcollect.GraphEditor
import com.signalcollect.triplerush.ChildIdReply
import com.signalcollect.triplerush.util.MemoryEfficientSplayIntSet
import com.signalcollect.util.SplayIntSet
import com.signalcollect.util.Ints
import com.signalcollect.util.FastInsertIntSet
import com.signalcollect.util.SplayNode

/**
 * Stores the SplayIntSet with the optimized child deltas in the state.
 */
abstract class OptimizedIndexVertex(
    id: Long) extends IndexVertex[Any](id) {

  def handleChildIdRequest(requestor: Long, graphEditor: GraphEditor[Long, Any]): Unit = {
    val childIds: Array[Int] = {
      state match {
        case i: Int =>
          Array(i)
        case a: Array[Byte] =>
          new FastInsertIntSet(a).toBuffer.toArray
        case s: SplayIntSet =>
          s.toBuffer.toArray
        case _ => Array[Int]() // Root vertex in an empty store.
      }
    }
    graphEditor.sendSignal(ChildIdReply(childIds), requestor)
  }

  override def edgeCount = {
    if (state != null) numberOfStoredChildDeltas else 0
  }

  def cardinality = numberOfStoredChildDeltas

  @inline final def numberOfStoredChildDeltas = {
    state match {
      case i: Int =>
        if (i != 0) 1 else 0
      case a: Array[Byte] =>
        val count = new FastInsertIntSet(a).size
        count
      case s: SplayIntSet =>
        val count = s.size
        count
      case _ => 0
    }
  }

  @inline def foreachChildDelta(f: Int => Unit) = {
    state match {
      case i: Int =>
        f(i) // No check for 0, as an index vertex always needs to have at elast one child delta set at this point.
      case a: Array[Byte] =>
        new FastInsertIntSet(a).foreach(f)
      case s: SplayIntSet =>
        s.foreach(f)
      case _ =>
    }
  }

  def addChildDelta(delta: Int): Boolean = {
    val countBefore = edgeCount
    if (state == null) {
      state = delta
      true
    } else {
      state match {
        case i: Int =>
          if (delta != i) {
            val expectedCountAfter = edgeCount + 1
            var intSet = Ints.createEmptyFastInsertIntSet
            intSet = new FastInsertIntSet(intSet).insert(i, 0.01f)
            state = new FastInsertIntSet(intSet).insert(delta, 0.01f)
            val actualCountAfter = edgeCount
            assert(actualCountAfter == expectedCountAfter, s"count should be $expectedCountAfter, but was $actualCountAfter")
            true
          } else {
            val countAfter = edgeCount
            assert(countBefore == countAfter)
            false
          }
        case a: Array[Byte] =>
          val sizeBefore = new FastInsertIntSet(a).size
          val didContainAlready = new FastInsertIntSet(a).contains(delta)
          val intSetAfter = new FastInsertIntSet(a).insert(delta, 0.01f)
          val sizeAfter = new FastInsertIntSet(intSetAfter).size
          if (didContainAlready) {
            assert(sizeAfter == sizeBefore)
          } else {
            assert(sizeAfter == sizeBefore + 1)
          }
          if (sizeAfter >= 1000) {
            val splayIntSet = new MemoryEfficientSplayIntSet
            val root = new SplayNode(intSetAfter) 
            splayIntSet.initializeWithRoot(root)
            assert(splayIntSet.size == sizeAfter)
            state = splayIntSet
          } else {
            state = intSetAfter
          }
          sizeAfter > sizeBefore
        case s: SplayIntSet =>
          val wasInserted = s.insert(delta)
          wasInserted
        case _ => false
      }
    }
  }

}
