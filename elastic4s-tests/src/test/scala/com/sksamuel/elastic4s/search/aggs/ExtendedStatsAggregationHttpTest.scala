package com.sksamuel.elastic4s.search.aggs

import com.sksamuel.elastic4s.RefreshPolicy
import com.sksamuel.elastic4s.http.ElasticDsl
import com.sksamuel.elastic4s.testkit.DiscoveryLocalNodeProvider
import org.scalatest.{FreeSpec, Matchers}

import scala.util.Try

class ExtendedStatsAggregationHttpTest extends FreeSpec with DiscoveryLocalNodeProvider with Matchers with ElasticDsl {

  Try {
    http.execute {
      deleteIndex("extendedstatsagg")
    }.await
  }

  http.execute {
    createIndex("extendedstatsagg") mappings {
      mapping("sales_per_month") fields(
        dateField("month"),
        doubleField("sales").stored(true)
      )
    }
  }.await

  // based on the example from extended stats agg documentation
  http.execute(
    bulk(
      indexInto("extendedstatsagg/sales_per_month") fields("month" -> "2017-01-01", "sales" -> 550.0),
      indexInto("extendedstatsagg/sales_per_month") fields("month" -> "2017-02-01", "sales" -> 60.0),
      indexInto("extendedstatsagg/sales_per_month") fields("month" -> "2017-03-01", "sales" -> 375.0)
    ).refresh(RefreshPolicy.Immediate)
  ).await

  "extended stats agg" - {
    "should return the expected stats" in {

      val resp = http.execute {
        search("extendedstatsagg").matchAllQuery().aggs {
          extendedStatsAgg("agg1", "sales")
        }
      }.await.right.get.result
      resp.totalHits shouldBe 3
      val agg = resp.aggs.extendedStats("agg1")
      agg.count shouldBe 3
      agg.min shouldBe 60.0
      agg.max shouldBe 550.0
      math.abs(agg.avg - 328.333) < 0.1 shouldBe true
      agg.sum shouldBe 985.0
      agg.sumOfSquares shouldBe 446725.0
      math.abs(agg.variance - 41105.555) < 0.1 shouldBe true
      math.abs(agg.stdDeviation - 202.745) < 0.1 shouldBe true
    }
  }
}
