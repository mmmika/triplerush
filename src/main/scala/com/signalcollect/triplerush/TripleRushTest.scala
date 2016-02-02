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

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import akka.actor.actorRef2Scala
import akka.cluster.sharding.ClusterSharding
import akka.pattern.ask
import akka.util.Timeout
import com.signalcollect.triplerush.index.Index
import scala.concurrent.Await
import scala.concurrent.Future
import akka.stream.scaladsl.Sink
import akka.stream.ActorMaterializerSettings
import akka.stream.ActorMaterializer
import akka.actor.ActorSystem
import akka.stream.Supervision

object TripleRushTest extends App {

  val tr = TripleRush()

  val futures = List(
    tr.addTriplePattern(TriplePattern(1, 2, 3)),
    tr.addTriplePattern(TriplePattern(1, 2, 4)),
    tr.addTriplePattern(TriplePattern(1, 5, 3)),
    tr.addTriplePattern(TriplePattern(6, 2, 3)))
  Await.ready(Future.sequence(futures), 30.seconds)

  println("Index creation successful")
  
  val results = tr.query(List(TriplePattern(-1, 2, 3)), 1, Int.MaxValue)
  
  println("Query was dispatched")

  implicit val system = ActorSystem("test")
  implicit val materializer = ActorMaterializer()
  
  Thread.sleep(5000)
  
  val printingSink = Sink.foreach[Array[Int]](println)
    //Sink.fold[Vector[Array[Int], Array[Int]]](Vector.empty[Array[Int]])(_ + _)
  results.runWith(printingSink)
      //f)(result => println(s"result = $result"))
 
}
