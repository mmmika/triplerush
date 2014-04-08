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

package com.signalcollect.triplerush.sparql

import scala.util.parsing.combinator.ImplicitConversions
import com.signalcollect.triplerush.TriplePattern

case class ParsedSparqlQuery(prefixes: List[PrefixDeclaration], select: Select)

sealed trait VariableOrBound

case class Variable(name: String) extends VariableOrBound

case class Iri(url: String) extends VariableOrBound

//case class IntLiteral(i: Int) extends VariableOrBound
//
//case class StringLiteral(string: String) extends VariableOrBound

case class ParsedPattern(s: VariableOrBound, p: VariableOrBound, o: VariableOrBound)

case class PrefixDeclaration(prefix: String, expanded: String)

case class Select(
  selectVariables: List[Variable],
  patternUnions: List[List[ParsedPattern]],
  isDistinct: Boolean = false,
  orderBy: Option[Variable] = None,
  limit: Option[Int] = None)

object SparqlParser extends ParseHelper[ParsedSparqlQuery] with ImplicitConversions {

  lexical.delimiters ++= List(
    "(", ")", ",", ":", "<", ">")
  protected override val whiteSpace = """(\s|//.*|(?m)/\*(\*(?!/)|[^*])*\*/)+""".r

  def defaultParser = sparqlQuery

  val prefix = "PREFIX" // | "prefix"
  val select = "SELECT" // | "select"
  val where = "WHERE" // | "where"
  val distinct = "DISTINCT" // | "distinct"
  val union = "UNION" // | "union"
  val orderBy = "ORDER" ~> "BY"
  val limit = "LIMIT"

  val url: Parser[String] = "[-a-zA-Z0-9:/\\.#]*".r

  val iri: Parser[Iri] = {
    (("<" ~> url <~ ">") | url) ^^ {
      case url => Iri(url)
    }
  }

  //  val string = """.*""".r
  //
  //  val stringLiteral: Parser[StringLiteral] = {
  //    "\"" ~> string <~ "\"" ^^ {
  //      case l => StringLiteral(l)
  //    }
  //  }

  val variable: Parser[Variable] = {
    "?" ~> identifier ^^ {
      case variableName =>
        Variable(variableName)
    }
  }

  val prefixDeclaration: Parser[PrefixDeclaration] = {
    ((prefix ~> identifier) <~ ":" ~! "<") ~! url <~ ">" ^^ {
      case prefix ~ expanded =>
        PrefixDeclaration(prefix, expanded)
    }
  }

  val variableOrBound: Parser[VariableOrBound] = {
    variable | iri
  }

  val pattern: Parser[ParsedPattern] = {
    variableOrBound ~! variableOrBound ~! variableOrBound ^^ {
      case s ~ p ~ o =>
        ParsedPattern(s, p, o)
    }
  }

  val patternList: Parser[List[ParsedPattern]] = {
    ("{" ~> rep1sep(pattern, ".")) <~ "}" ^^ {
      case patterns =>
        patterns
    }
  }

  val unionOfPatternLists: Parser[List[List[ParsedPattern]]] = {
    patternList ^^ { List(_) } |
      "{" ~> rep1sep(patternList, union) <~ "}"
  }

  val selectDeclaration: Parser[Select] = {
    ((select ~> opt(distinct) ~ rep1sep(variable, opt(","))) <~ where) ~! unionOfPatternLists ~
      opt(orderBy ~> variable) ~
      opt(limit ~> integer) <~
      opt(";") ^^ {
        case distinct ~ selectVariables ~ unionOfPatterns ~ orderBy ~ limit =>
          Select(selectVariables, unionOfPatterns, distinct.isDefined, orderBy, limit)
      }
  }

  val sparqlQuery: Parser[ParsedSparqlQuery] = {
    rep(prefixDeclaration) ~! selectDeclaration ^^ {
      case prefixes ~ select =>
        ParsedSparqlQuery(prefixes, select)
    }
  }
}