package io.getquill.source.sql.idiom

import io.getquill.ast
import io.getquill.ast.Action
import io.getquill.ast.Assignment
import io.getquill.ast.Ast
import io.getquill.ast.BinaryOperation
import io.getquill.ast.BinaryOperator
import io.getquill.ast.Constant
import io.getquill.ast.Delete
import io.getquill.ast.Entity
import io.getquill.ast.Filter
import io.getquill.ast._
import io.getquill.ast.Insert
import io.getquill.ast.NullValue
import io.getquill.ast.Property
import io.getquill.ast.Query
import io.getquill.ast.Tuple
import io.getquill.ast.UnaryOperation
import io.getquill.ast.UnaryOperator
import io.getquill.ast.Update
import io.getquill.ast.Value
import io.getquill.util.Messages.fail
import io.getquill.util.Show.Show
import io.getquill.util.Show.Shower
import io.getquill.util.Show.listShow
import io.getquill.source.sql._

trait SqlIdiom {

  implicit def astShow(implicit propertyShow: Show[Property]): Show[Ast] =
    new Show[Ast] {
      def show(e: Ast) =
        e match {
          case query: Query                            => s"${SqlQuery(query).show}"
          case UnaryOperation(op, ast)                 => s"${op.show} ${scopedShow(ast)}"
          case BinaryOperation(a, ast.`==`, NullValue) => s"${scopedShow(a)} IS NULL"
          case BinaryOperation(NullValue, ast.`==`, b) => s"${scopedShow(b)} IS NULL"
          case BinaryOperation(a, ast.`!=`, NullValue) => s"${scopedShow(a)} IS NOT NULL"
          case BinaryOperation(NullValue, ast.`!=`, b) => s"${scopedShow(b)} IS NOT NULL"
          case BinaryOperation(a, op, b)               => s"${scopedShow(a)} ${op.show} ${scopedShow(b)}"
          case a: Action                               => a.show
          case ident: Ident                            => ident.show
          case property: Property                      => property.show
          case value: Value                            => value.show
          case other                                   => fail(s"Malformed query $other.")
        }
    }

  implicit val sqlQueryShow: Show[SqlQuery] = new Show[SqlQuery] {
    def show(e: SqlQuery) = {
      val selectFrom = s"SELECT ${e.select.show} FROM ${e.from.show}"
      val where =
        e.where match {
          case None        => selectFrom
          case Some(where) => selectFrom + s" WHERE ${where.show}"
        }
      val orderBy =
        e.orderBy match {
          case Nil     => where
          case orderBy => where + showOrderBy(orderBy)
        }
      e.limit match {
        case None        => orderBy
        case Some(limit) => s"$orderBy LIMIT ${limit.show}"
      }
    }
  }

  protected def showOrderBy(criterias: List[OrderByCriteria]) =
    s" ORDER BY ${criterias.show}"

  implicit val sourceShow: Show[Source] = new Show[Source] {
    def show(source: Source) =
      source match {
        case TableSource(table, alias) => s"$table $alias"
        case QuerySource(q, alias)     => s"(${q.show}) $alias"
      }
  }

  implicit val orderByCriteriaShow: Show[OrderByCriteria] = new Show[OrderByCriteria] {
    def show(criteria: OrderByCriteria) =
      criteria match {
        case OrderByCriteria(prop, true)  => s"${prop.show} DESC"
        case OrderByCriteria(prop, false) => prop.show
      }
  }

  implicit val unaryOperatorShow: Show[UnaryOperator] = new Show[UnaryOperator] {
    def show(o: UnaryOperator) =
      o match {
        case io.getquill.ast.`!`        => "NOT"
        case io.getquill.ast.`isEmpty`  => "NOT EXISTS"
        case io.getquill.ast.`nonEmpty` => "EXISTS"
      }
  }

  implicit val binaryOperatorShow: Show[BinaryOperator] = new Show[BinaryOperator] {
    def show(o: BinaryOperator) =
      o match {
        case ast.`-`  => "-"
        case ast.`+`  => "+"
        case ast.`*`  => "*"
        case ast.`==` => "="
        case ast.`!=` => "<>"
        case ast.`&&` => "AND"
        case ast.`||` => "OR"
        case ast.`>`  => ">"
        case ast.`>=` => ">="
        case ast.`<`  => "<"
        case ast.`<=` => "<="
        case ast.`/`  => "/"
        case ast.`%`  => "%"
      }
  }

  implicit def propertyShow(implicit valueShow: Show[Value], identShow: Show[Ident]): Show[Property] =
    new Show[Property] {
      def show(e: Property) =
        e match {
          case Property(ast, name) => s"${scopedShow(ast)}.$name"
        }
    }

  implicit val valueShow: Show[Value] = new Show[Value] {
    def show(e: Value) =
      e match {
        case Constant(v: String) => s"'$v'"
        case Constant(())        => s"1"
        case Constant(v)         => s"$v"
        case NullValue           => s"null"
        case Tuple(values)       => s"${values.show}"
      }
  }

  implicit val identShow: Show[Ident] = new Show[Ident] {
    def show(e: Ident) = e.name
  }

  implicit val actionShow: Show[Action] = {

    def set(assignments: List[Assignment]) =
      assignments.map(a => s"${a.property} = ${a.value.show}").mkString(", ")

    implicit def propertyShow: Show[Property] = new Show[Property] {
      def show(e: Property) =
        e match {
          case Property(_, name) => name
        }
    }

    new Show[Action] {
      def show(a: Action) =
        (a: @unchecked) match {

          case Insert(Entity(table), assignments) =>
            val columns = assignments.map(_.property)
            val values = assignments.map(_.value)
            s"INSERT INTO $table (${columns.mkString(",")}) VALUES (${values.show})"

          case Update(Entity(table), assignments) =>
            s"UPDATE $table SET ${set(assignments)}"

          case Update(Filter(Entity(table), x, where), assignments) =>
            s"UPDATE $table SET ${set(assignments)} WHERE ${where.show}"

          case Delete(Filter(Entity(table), x, where)) =>
            s"DELETE FROM $table WHERE ${where.show}"

          case Delete(Entity(table)) =>
            s"DELETE FROM $table"
        }
    }
  }

  private def scopedShow[A <: Ast](ast: A)(implicit show: Show[A]) =
    ast match {
      case _: Query           => s"(${ast.show})"
      case _: BinaryOperation => s"(${ast.show})"
      case other              => ast.show
    }

}
