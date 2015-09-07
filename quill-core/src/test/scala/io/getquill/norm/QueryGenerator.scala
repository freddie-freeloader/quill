package io.getquill.norm

import scala.util.Random
import io.getquill.ast
import io.getquill.ast.BinaryOperation
import io.getquill.ast.Constant
import io.getquill.ast.Entity
import io.getquill.ast.Filter
import io.getquill.ast.FlatMap
import io.getquill.ast.Ident
import io.getquill.ast.Map
import io.getquill.ast.NullValue
import io.getquill.ast.Property
import io.getquill.ast.Query
import io.getquill.ast.SortBy
import io.getquill.ast.Reverse
import io.getquill.ast.Take

class QueryGenerator(seed: Int) {

  private val random = new Random(seed)

  def apply(i: Int): Query =
    if (i <= 2) {
      Entity(string(3))
    } else {
      random.nextInt(6) match {
        case 0 => map(i)
        case 1 => flatMap(i)
        case 2 => filter(i)
        case 3 => sortBy(i)
        case 4 => reverse(i)
        case 5 => take(i)
      }
    }

  private def take(i: Int) =
    Take(apply(i - 1), Constant(random.nextInt(20)))

  private def map(i: Int) = {
    val id = ident
    Map(apply(i), id, id)
  }

  private def flatMap(i: Int) = {
    val (a, b) = distribute(i)
    FlatMap(apply(a), ident, apply(b))
  }

  private def filter(i: Int) = {
    val id = ident
    Filter(apply(i), id, BinaryOperation(Property(id, string), ast.`!=`, Constant(1)))
  }

  private def sortBy(i: Int) = {
    val id = ident
    SortBy(apply(i), id, Property(id, string))
  }

  private def reverse(i: Int) =
    Reverse(sortBy(i - 1))

  private def distribute(i: Int) = {
    val j = random.nextInt(i - 2) + 1
    val k = i - j
    (j, k)
  }

  private def ident =
    Ident(string)

  private def string(size: Int): String =
    size match {
      case 0    => ""
      case size => string + string(size - 1)
    }

  private def string: String = {
    val letters = "abcdefghijklmnopqrstuvwxyz"
    letters.charAt(random.nextInt(letters.size)).toString
  }

}
