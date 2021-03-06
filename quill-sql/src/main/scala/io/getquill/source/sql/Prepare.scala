package io.getquill.source.sql

import io.getquill.ast._
import io.getquill.norm.Normalize
import io.getquill.source.BindVariables
import io.getquill.naming.NamingStrategy
import io.getquill.source.sql.idiom.SqlIdiom
import io.getquill.util.Show._
import io.getquill.util.Messages._
import io.getquill.norm.capture.AvoidAliasConflict
import io.getquill.norm.capture.AvoidCapture
import io.getquill.norm.FlattenOptionOperation
import io.getquill.source.sql.norm.ExpandOuterJoin
import io.getquill.source.sql.norm.ExpandNestedQueries

object Prepare {

  def apply(ast: Ast, params: List[Ident])(implicit d: SqlIdiom, n: NamingStrategy) = {
    import d._
    val (bindedAst, idents) = BindVariables(ast, params)
    val sqlString =
      normalize(bindedAst) match {
        case q: Query =>
          val sql = SqlQuery(q)
          VerifySqlQuery(sql).map(fail)
          ExpandNestedQueries(sql, Set.empty).show
        case other =>
          other.show
      }
    (sqlString, idents)
  }

  private[this] val normalize =
    (identity[Ast] _)
      .andThen(Normalize.apply _)
      .andThen(ExpandOuterJoin.apply _)
      .andThen(Normalize.apply _)
      .andThen(FlattenOptionOperation.apply _)
}
