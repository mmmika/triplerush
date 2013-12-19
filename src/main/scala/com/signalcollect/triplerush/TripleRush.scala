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

package com.signalcollect.triplerush

import java.io.DataInputStream
import java.io.EOFException
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import scala.Array.canBuildFrom
import scala.collection.mutable.UnrolledBuffer
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.future
import scala.util.Random
import org.semanticweb.yars.nx.parser.NxParser
import com.signalcollect.ExecutionConfiguration
import com.signalcollect.GraphBuilder
import com.signalcollect.GraphEditor
import com.signalcollect.Vertex
import com.signalcollect.configuration.ActorSystemRegistry
import com.signalcollect.configuration.ExecutionMode
import com.signalcollect.triplerush.QueryParticle.arrayToParticle
import com.signalcollect.triplerush.vertices.Forwarding
import com.signalcollect.triplerush.vertices.POIndex
import com.signalcollect.triplerush.vertices.QueryDone
import com.signalcollect.triplerush.vertices.QueryOptimizers
import com.signalcollect.triplerush.vertices.QueryResult
import com.signalcollect.triplerush.vertices.QueryVertex
import com.signalcollect.triplerush.vertices.SOIndex
import com.signalcollect.triplerush.vertices.SPIndex
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import java.io.File

import scala.collection.immutable.TreeMap
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration

case object RegisterQueryResultRecipient

class ResultRecipientActor extends Actor {
  var queryDone: QueryDone = _
  var queryResultRecipient: ActorRef = _

  var queries = UnrolledBuffer[Array[Int]]()

  def receive = {
    case RegisterQueryResultRecipient =>
      queryResultRecipient = sender
      if (queryDone != null) {
        queryResultRecipient ! QueryResult(queries, queryDone.statKeys, queryDone.statVariables)
        self ! PoisonPill
      }
    case queryDone: QueryDone =>
      this.queryDone = queryDone
      if (queryResultRecipient != null) {
        queryResultRecipient ! QueryResult(queries, queryDone.statKeys, queryDone.statVariables)
        self ! PoisonPill
      }

    case queryResults: Array[Array[Int]] =>
      // TODO: Send only bindings instead of full particles.
      val newBuffer = {
        if (queryResults.length == 1) {
          UnrolledBuffer(queryResults(0))
        } else {
          queryResults map (UnrolledBuffer(_)) reduce (_.concat(_))
        }
      }
      queries = queries.concat(newBuffer)
  }
}

case object UndeliverableRerouter {
  def handle(signal: Any, targetId: Any, sourceId: Option[Any], graphEditor: GraphEditor[Any, Any]) {
    // TODO: Handle root pattern.
    if (targetId == TriplePattern(0, 0, 0)) {
      throw new Exception("Root pattern is not supported.")
    }
    signal match {
      case queryParticle: Array[Int] =>
        graphEditor.sendSignal(queryParticle.tickets, queryParticle.queryId, None)
      case CardinalityRequest(forPattern: TriplePattern, requestor: AnyRef) =>
        graphEditor.sendSignal(CardinalityReply(forPattern, 0), requestor, None)
      case CardinalityReply(forPattern, cardinality) =>
      // Do nothing, query vertex has removed itself already because of a 0 cardinality pattern.
      case other =>
        println(s"Failed signal delivery of $other of type ${other.getClass} to the vertex with id $targetId and sender id $sourceId.")
    }
  }
}

/**
 * Only works if the file contains at least one triple.
 */
case class BinarySplitLoader(binaryFilename: String) extends Iterator[GraphEditor[Any, Any] => Unit] {

  var is: FileInputStream = _
  var dis: DataInputStream = _

  var isInitialized = false

  protected def readNextTriplePattern: TriplePattern = {
    try {
      val sId = dis.readInt
      val pId = dis.readInt
      val oId = dis.readInt
      TriplePattern(sId, pId, oId)
    } catch {
      case done: EOFException =>
        dis.close
        is.close
        null.asInstanceOf[TriplePattern]
      case t: Throwable =>
        println(t)
        throw t
    }
  }

  var nextTriplePattern: TriplePattern = null

  def initialize {
    is = new FileInputStream(binaryFilename)
    dis = new DataInputStream(is)
    nextTriplePattern = readNextTriplePattern
    isInitialized = true
  }

  def hasNext = {
    if (!isInitialized) {
      true
    } else {
      nextTriplePattern != null
    }
  }

  def next: GraphEditor[Any, Any] => Unit = {
    if (!isInitialized) {
      initialize
    }
    val patternCopy = nextTriplePattern
    val loader: GraphEditor[Any, Any] => Unit = FileLoaders.addTriple(patternCopy, _)
    nextTriplePattern = readNextTriplePattern
    loader
  }
}

case object FileLoaders {
  def loadNtriplesFile(ntriplesFilename: String)(graphEditor: GraphEditor[Any, Any]) {
    val is = new FileInputStream(ntriplesFilename)
    val nxp = new NxParser(is)
    println(s"Reading triples from $ntriplesFilename ...")
    var triplesLoaded = 0
    while (nxp.hasNext) {
      val triple = nxp.next
      val predicateString = triple(1).toString
      val subjectString = triple(0).toString
      val objectString = triple(2).toString
      val sId = Mapping.register(subjectString)
      val pId = Mapping.register(predicateString)
      val oId = Mapping.register(objectString)
      addTriple(TriplePattern(sId, pId, oId), graphEditor)
      triplesLoaded += 1
      if (triplesLoaded % 10000 == 0) {
        println(s"Loaded $triplesLoaded triples from file $ntriplesFilename ...")
      }
    }
    println(s"Done loading triples from $ntriplesFilename. Loaded a total of $triplesLoaded triples.")
    is.close
  }

  def addTriple(tp: TriplePattern, graphEditor: GraphEditor[Any, Any]) {
    assert(tp.isFullyBound)
    val po = TriplePattern(0, tp.p, tp.o)
    val so = TriplePattern(tp.s, 0, tp.o)
    val sp = TriplePattern(tp.s, tp.p, 0)
    graphEditor.addVertex(new POIndex(po))
    graphEditor.addVertex(new SOIndex(so))
    graphEditor.addVertex(new SPIndex(sp))
    graphEditor.addEdge(po, new PlaceholderEdge(tp.s))
    graphEditor.addEdge(so, new PlaceholderEdge(tp.p))
    graphEditor.addEdge(sp, new PlaceholderEdge(tp.o))
  }
}

//for statistics gathering
case class PredicatePair(first: Int, second: Int)

case class TripleRush(
  graphBuilder: GraphBuilder[Any, Any] = GraphBuilder,
  //.withLoggingLevel(Logging.DebugLevel)
  console: Boolean = false) extends QueryEngine {

  var canExecute = false

  def prepareExecution {
    g.awaitIdle
    g.execute(ExecutionConfiguration.withExecutionMode(ExecutionMode.ContinuousAsynchronous))
    g.awaitIdle
    canExecute = true
  }

  // TODO: Handle root pattern(s).
  // TODO: Validate/simplify queries before executing them.

  println("Graph engine is initializing ...")
  private val g = graphBuilder.withConsole(console).
    withMessageBusFactory(new CombiningMessageBusFactory(8096, false)).
    withMapperFactory(TripleMapperFactory).
    //    withMessageSerialization(true).
    //    withJavaSerialization(false).
    withHeartbeatInterval(500).
    withKryoRegistrations(List(
      "com.signalcollect.triplerush.vertices.SIndex",
      "com.signalcollect.triplerush.vertices.PIndex",
      "com.signalcollect.triplerush.vertices.OIndex",
      "com.signalcollect.triplerush.vertices.SPIndex",
      "com.signalcollect.triplerush.vertices.POIndex",
      "com.signalcollect.triplerush.vertices.SOIndex",
      "com.signalcollect.triplerush.TriplePattern",
      "com.signalcollect.triplerush.vertices.QueryVertex",
      //"com.signalcollect.triplerush.vertices.QueryOptimizer",
      "com.signalcollect.triplerush.vertices.QueryDone",
      "com.signalcollect.triplerush.PlaceholderEdge",
      "com.signalcollect.triplerush.CardinalityRequest",
      "com.signalcollect.triplerush.CardinalityReply",
      "com.signalcollect.triplerush.vertices.QueryResult",
      "com.signalcollect.triplerush.TriplePattern",
      "Array[com.signalcollect.triplerush.TriplePattern]",
      "com.signalcollect.interfaces.SignalMessage$mcIJ$sp",
      "akka.actor.RepointableActorRef")).build
  g.setUndeliverableSignalHandler(UndeliverableRerouter.handle _)
  val system = ActorSystemRegistry.retrieve("SignalCollect").get
  implicit val executionContext = system.dispatcher
  println("TripleRush is ready.")

  def loadNtriples(ntriplesFilename: String, placementHint: Option[Any] = None) {
    g.modifyGraph(FileLoaders.loadNtriplesFile(ntriplesFilename) _, placementHint)
  }

  def loadBinary(binaryFilename: String, placementHint: Option[Any] = None) {
    g.loadGraph(BinarySplitLoader(binaryFilename), placementHint)
  }

  /**statistics generation queries*/
  val x = -1

  def bindingsToMap(bindings: Array[Int]): Map[Int, Int] = {
    (((-1 to -bindings.length by -1).zip(bindings))).toMap
  }

  def getBindingsFor(variable: Int, bindings: UnrolledBuffer[Array[Int]]): Set[Int] = {
    val allBindings: List[Map[Int, Int]] = bindings.toList.map(bindingsToMap(_).map(entry => (entry._1, entry._2)))
    val listOfSetsOfKeysWithVar: List[Set[Int]] = allBindings.map {
      bindings: Map[Int, Int] =>
        bindings.filterKeys(_ == variable).values.toSet
    }
    listOfSetsOfKeysWithVar.foldLeft(Set[Int]())(_ union _)
  }

  def resultFromQuery(q: QuerySpecification): QueryResult = {
    val resultFromQueryEngine = executeQuery(queryToGetAllPredicates.toParticle, true)
    val result = Await.result(resultFromQueryEngine, new FiniteDuration(100, TimeUnit.SECONDS))
    result
  }
  def resultFromQuery(q: QuerySpecification, resultFuture: Future[QueryResult]): QueryResult = {
    val result = Await.result(resultFuture, new FiniteDuration(100, TimeUnit.SECONDS))
    result
  }

  val queryToGetAllPredicates = QuerySpecification(List(TriplePattern(0, x, 0)))
  //val allPredicatesQueryResult = executeQuery(queryToGetAllPredicates.toParticle, true)
  val allPredicateResult = resultFromQuery(queryToGetAllPredicates)
  //val allPredicateResult = resultFromQuery(queryToGetAllPredicates, allPredicatesQueryResult)
  val bindingsForPredicates = getBindingsFor(x, allPredicateResult.bindings)

  val mapOutOut = collection.mutable.Map[PredicatePair, Int]()
  val mapInOut = collection.mutable.Map[PredicatePair, Int]()
  val mapInIn = collection.mutable.Map[PredicatePair, Int]()
  val mapOutIn = collection.mutable.Map[PredicatePair, Int]()
  val mapPredicateBranching = collection.mutable.Map[Int, Int]()

  for (p1 <- bindingsForPredicates) {
    for (p2 <- bindingsForPredicates) {
      if (p1 != p2) {
        val outOutQuery = QuerySpecification(List(TriplePattern(x, p1, 0), TriplePattern(x, p2, 0)))
        val inOutQuery = QuerySpecification(List(TriplePattern(0, p1, x), TriplePattern(x, p2, 0)))
        val inInQuery = QuerySpecification(List(TriplePattern(0, p1, x), TriplePattern(0, p2, x)))
        val outInQuery = QuerySpecification(List(TriplePattern(x, p1, 0), TriplePattern(0, p2, x)))

        val resultOutOutQuery = resultFromQuery(outOutQuery)
        val resultInOutQuery = resultFromQuery(inOutQuery)
        val resultInInQuery = resultFromQuery(inInQuery)
        val resultOutInQuery = resultFromQuery(outInQuery)

        mapOutOut += PredicatePair(p1, p2) -> resultOutOutQuery.bindings.length
        mapInOut += PredicatePair(p1, p2) -> resultInOutQuery.bindings.length
        mapInIn += PredicatePair(p1, p2) -> resultInInQuery.bindings.length
        mapOutIn += PredicatePair(p1, p2) -> resultOutInQuery.bindings.length
      }
    }
    
    /**need predicate branching statistics gathering here*/
    val predicateBranchingQuery = QuerySpecification(List(TriplePattern(0, p1, 0)))
    //val resultPredicateBranchingQuery = executeQuery(predicateBranchingQuery)
    val resultPredicateBranchingQuery = resultFromQuery(predicateBranchingQuery)
    //mapPredicateBranching += p1 -> resultPredicateBranchingQuery.bindings.length
    mapPredicateBranching += p1 -> resultPredicateBranchingQuery.bindings.length
  }
  /** end of statistics gathering */

  /**
   * Slow, only use for debugging purposes.
   */
  def addTriple(s: String, p: String, o: String) {
    val sId = Mapping.register(s)
    val pId = Mapping.register(p)
    val oId = Mapping.register(o)
    val tp = TriplePattern(sId, pId, oId)
    FileLoaders.addTriple(tp, g)
  }

  /**
   * Slow, only use for debugging purposes.
   */
  def addEncodedTriple(sId: Int, pId: Int, oId: Int) {
    FileLoaders.addTriple(TriplePattern(sId, pId, oId), g)
  }

  def executeQuery(q: Array[Int], optimizer: Boolean) = {
    if (optimizer) {
      executeQuery(q, QueryOptimizers.Clever)
    } else {
      executeQuery(q, QueryOptimizers.None)
    }
  }

  def executeQuery(q: Array[Int], optimizer: Int = QueryOptimizers.Clever): Future[QueryResult] = {
    assert(canExecute, "Call TripleRush.prepareExecution before executing queries.")
    if (!q.isResult) {
      val resultRecipientActor = system.actorOf(Props[ResultRecipientActor], name = Random.nextLong.toString)
      // TODO: Add callback that removes the query vertex and result recipient actor.
      g.addVertex(new QueryVertex(q, resultRecipientActor, optimizer, recordStats = true))
      implicit val timeout = Timeout(Duration.create(7200, TimeUnit.SECONDS))
      val resultFuture = resultRecipientActor ? RegisterQueryResultRecipient
      resultFuture.asInstanceOf[Future[QueryResult]]
    } else {
      Future.successful(QueryResult(UnrolledBuffer(), Array(), Array()))
    }
  }

  def awaitIdle {
    g.awaitIdle
  }

  def shutdown = g.shutdown

}
