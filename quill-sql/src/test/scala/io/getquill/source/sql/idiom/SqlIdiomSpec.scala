package io.getquill.source.sql.idiom

import io.getquill._
import io.getquill.Spec
import io.getquill.source.sql.mirror.mirrorSource.run
import io.getquill.source.sql.mirror.mirrorSource
import io.getquill.quotation.Quoted
import io.getquill.norm.Normalize

class SqlIdiomSpec extends Spec {

  "shows the sql representation of normalized asts" - {
    "query" - {
      "without filter" in {
        mirrorSource.run(qr1).sql mustEqual
          "SELECT x.s, x.i, x.l FROM TestEntity x"
      }
      "with filter" in {
        val q = quote {
          qr1.filter(t => t.s == "s")
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT t.s, t.i, t.l FROM TestEntity t WHERE t.s = 's'"
      }
      "multiple entities" in {
        val q = quote {
          for {
            a <- qr1
            b <- qr2 if (a.s == b.s)
          } yield {
            a.s
          }
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT a.s FROM TestEntity a, TestEntity2 b WHERE a.s = b.s"
      }
      "sorted" - {
        "simple" in {
          val q = quote {
            qr1.filter(t => t.s != null).sortBy(_.s)
          }
          mirrorSource.run(q).sql mustEqual
            "SELECT t.s, t.i, t.l FROM TestEntity t WHERE t.s IS NOT NULL ORDER BY t.s"
        }
        "nested" in {
          val q = quote {
            for {
              a <- qr1.sortBy(t => t.s).reverse
              b <- qr2.sortBy(t => t.i)
              if (a.l == b.l)
            } yield {
              (a.s, b.i)
            }
          }
          mirrorSource.run(q).sql mustEqual
            "SELECT t.s, t1.i FROM TestEntity t, TestEntity2 t1 WHERE t.l = t1.l ORDER BY t.s DESC, t1.i"
        }
      }
      "limited" - {
        "simple" in {
          val q = quote {
            qr1.take(1)
          }
          mirrorSource.run(q).sql mustEqual "SELECT x.s, x.i, x.l FROM TestEntity x LIMIT 1"
        }
        "nested" in {
          val q = quote {
            for {
              a <- qr1.filter(t => t.s == "s").take(10)
              b <- qr2
            } yield {
              (a.s, b.s)
            }
          }
          mirrorSource.run(q).sql mustEqual "SELECT a.s, b.s FROM (SELECT * FROM TestEntity t WHERE t.s = 's' LIMIT 10) a, TestEntity2 b"
        }
        "complex" in {
          val q = quote {
            for {
              a <- qr1.filter(t => t.s == "s").sortBy(t => t.i).take(10)
              b <- qr2.filter(t => t.s == a.s).sortBy(t => t.s)
            } yield {
              (a.s, b.s)
            }
          }
          mirrorSource.run(q).sql mustEqual "SELECT a.s, t.s FROM (SELECT * FROM TestEntity t WHERE t.s = 's' ORDER BY t.i LIMIT 10) a, TestEntity2 t WHERE t.s = a.s ORDER BY t.s"
        }
      }
    }
    "unary operation" - {
      "!" in {
        val q = quote {
          qr1.filter(t => !(t.s == "a"))
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT t.s, t.i, t.l FROM TestEntity t WHERE NOT (t.s = 'a')"
      }
      "isEmpty" in {
        val q = quote {
          qr1.filter(t => qr2.filter(u => u.s == t.s).isEmpty)
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT t.s, t.i, t.l FROM TestEntity t WHERE NOT EXISTS (SELECT u FROM TestEntity2 u WHERE u.s = t.s)"
      }
      "nonEmpty" in {
        val q = quote {
          qr1.filter(t => qr2.filter(u => u.s == t.s).nonEmpty)
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT t.s, t.i, t.l FROM TestEntity t WHERE EXISTS (SELECT u FROM TestEntity2 u WHERE u.s = t.s)"
      }
    }
    "binary operation" - {
      "-" in {
        val q = quote {
          qr1.map(t => t.i - t.i)
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT t.i - t.i FROM TestEntity t"
      }
      "+" in {
        val q = quote {
          qr1.map(t => t.i + t.i)
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT t.i + t.i FROM TestEntity t"
      }
      "*" in {
        val q = quote {
          qr1.map(t => t.i * t.i)
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT t.i * t.i FROM TestEntity t"
      }
      "==" - {
        "null" - {
          "right" in {
            val q = quote {
              qr1.filter(t => t.s == null)
            }
            mirrorSource.run(q).sql mustEqual
              "SELECT t.s, t.i, t.l FROM TestEntity t WHERE t.s IS NULL"
          }
          "left" in {
            val q = quote {
              qr1.filter(t => null == t.s)
            }
            mirrorSource.run(q).sql mustEqual
              "SELECT t.s, t.i, t.l FROM TestEntity t WHERE t.s IS NULL"
          }
        }
        "values" in {
          val q = quote {
            qr1.filter(t => t.s == "s")
          }
          mirrorSource.run(q).sql mustEqual
            "SELECT t.s, t.i, t.l FROM TestEntity t WHERE t.s = 's'"
        }
      }
      "!=" - {
        "null" - {
          "right" in {
            val q = quote {
              qr1.filter(t => t.s != null)
            }
            mirrorSource.run(q).sql mustEqual
              "SELECT t.s, t.i, t.l FROM TestEntity t WHERE t.s IS NOT NULL"
          }
          "left" in {
            val q = quote {
              qr1.filter(t => null != t.s)
            }
            mirrorSource.run(q).sql mustEqual
              "SELECT t.s, t.i, t.l FROM TestEntity t WHERE t.s IS NOT NULL"
          }
        }
        "values" in {
          val q = quote {
            qr1.filter(t => t.s != "s")
          }
          mirrorSource.run(q).sql mustEqual
            "SELECT t.s, t.i, t.l FROM TestEntity t WHERE t.s <> 's'"
        }
      }
      "&&" in {
        val q = quote {
          qr1.filter(t => t.i != null && t.s == "s")
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT t.s, t.i, t.l FROM TestEntity t WHERE (t.i IS NOT NULL) AND (t.s = 's')"
      }
      "||" in {
        val q = quote {
          qr1.filter(t => t.i != null || t.s == "s")
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT t.s, t.i, t.l FROM TestEntity t WHERE (t.i IS NOT NULL) OR (t.s = 's')"
      }
      ">" in {
        val q = quote {
          qr1.filter(t => t.i > t.l)
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT t.s, t.i, t.l FROM TestEntity t WHERE t.i > t.l"
      }
      ">=" in {
        val q = quote {
          qr1.filter(t => t.i >= t.l)
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT t.s, t.i, t.l FROM TestEntity t WHERE t.i >= t.l"
      }
      "<" in {
        val q = quote {
          qr1.filter(t => t.i < t.l)
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT t.s, t.i, t.l FROM TestEntity t WHERE t.i < t.l"
      }
      "<=" in {
        val q = quote {
          qr1.filter(t => t.i <= t.l)
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT t.s, t.i, t.l FROM TestEntity t WHERE t.i <= t.l"
      }
      "/" in {
        val q = quote {
          qr1.filter(t => (t.i / t.l) == 0)
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT t.s, t.i, t.l FROM TestEntity t WHERE (t.i / t.l) = 0"
      }
      "%" in {
        val q = quote {
          qr1.filter(t => (t.i % t.l) == 0)
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT t.s, t.i, t.l FROM TestEntity t WHERE (t.i % t.l) = 0"
      }
    }
    "action" - {
      "insert" in {
        val q = quote {
          qr1.insert(_.i -> 1, _.s -> "s")
        }
        mirrorSource.run(q).sql mustEqual
          "INSERT INTO TestEntity (i,s) VALUES (1, 's')"
      }
      "update" - {
        "with filter" in {
          val q = quote {
            qr1.filter(t => t.s == null).update(_.s -> "s")
          }
          mirrorSource.run(q).sql mustEqual
            "UPDATE TestEntity SET s = 's' WHERE s IS NULL"
        }
        "without filter" in {
          val q = quote {
            qr1.update(_.s -> "s")
          }
          mirrorSource.run(q).sql mustEqual
            "UPDATE TestEntity SET s = 's'"
        }
      }
      "delete" - {
        "with filter" in {
          val q = quote {
            qr1.filter(t => t.s == null).delete
          }
          mirrorSource.run(q).sql mustEqual
            "DELETE FROM TestEntity WHERE s IS NULL"
        }
        "without filter" in {
          val q = quote {
            qr1.delete
          }
          mirrorSource.run(q).sql mustEqual
            "DELETE FROM TestEntity"
        }
      }
    }
    "ident" in {
      val q = quote {
        qr1.map(t => t.s).filter(s => s == null)
      }
      mirrorSource.run(q).sql mustEqual
        "SELECT t.s FROM TestEntity t WHERE t.s IS NULL"
    }
    "value" - {
      "constant" - {
        "string" in {
          val q = quote {
            qr1.map(t => "s")
          }
          mirrorSource.run(q).sql mustEqual
            "SELECT 's' FROM TestEntity t"
        }
        "unit" in {
          val q = quote {
            qr1.filter(t => qr1.map(u => {}).isEmpty)
          }
          mirrorSource.run(q).sql mustEqual
            "SELECT t.s, t.i, t.l FROM TestEntity t WHERE NOT EXISTS (SELECT 1 FROM TestEntity u)"
        }
        "value" in {
          val q = quote {
            qr1.map(t => 12)
          }
          mirrorSource.run(q).sql mustEqual
            "SELECT 12 FROM TestEntity t"
        }
      }
      "null" in {
        val q = quote {
          qr1.update(_.s -> null)
        }
        mirrorSource.run(q).sql mustEqual
          "UPDATE TestEntity SET s = null"
      }
      "tuple" in {
        val q = quote {
          qr1.map(t => (t.s, t.i))
        }
        mirrorSource.run(q).sql mustEqual
          "SELECT t.s, t.i FROM TestEntity t"
      }
    }
    "property" in {
      val q = quote {
        qr1.map(t => t.s)
      }
      mirrorSource.run(q).sql mustEqual
        "SELECT t.s FROM TestEntity t"
    }
  }

  "fails if the query is malformed" in {
    val q = quote {
      qr1.filter(t => t == ((s: String) => s))
    }
    "mirrorSource.run(q)" mustNot compile
  }
}
