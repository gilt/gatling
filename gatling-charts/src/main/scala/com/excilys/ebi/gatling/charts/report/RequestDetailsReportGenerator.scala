/**
 * Copyright 2011-2012 eBusiness Information, Groupe Excilys (www.excilys.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.excilys.ebi.gatling.charts.report

import com.excilys.ebi.gatling.charts.component.{ StatisticsTextComponent, Statistics, ComponentLibrary }
import com.excilys.ebi.gatling.charts.config.ChartsFiles.requestFile
import com.excilys.ebi.gatling.charts.series.Series
import com.excilys.ebi.gatling.charts.template.RequestDetailsPageTemplate
import com.excilys.ebi.gatling.charts.util.Colors.{ toString, YELLOW, TRANSLUCID_RED, TRANSLUCID_BLUE, RED, ORANGE, GREEN, BLUE }
import com.excilys.ebi.gatling.charts.util.StatisticsHelper.{ responseTimeByMillisecondAsList, respTimeAgainstNbOfReqPerSecond, latencyByMillisecondAsList }
import com.excilys.ebi.gatling.core.config.GatlingConfiguration.configuration
import com.excilys.ebi.gatling.core.result.message.RequestStatus.{ OK, KO }
import com.excilys.ebi.gatling.core.result.reader.{ DataReader, ChartRequestRecord }
import com.excilys.ebi.gatling.core.util.StringHelper.EMPTY

class RequestDetailsReportGenerator(runOn: String, dataReader: DataReader, componentLibrary: ComponentLibrary) extends ReportGenerator(runOn, dataReader, componentLibrary) {

	def generate {
		dataReader.requestNames.foreach { requestName =>
			val totalCount = dataReader.countRequests(None, Some(requestName))
			val okCount = dataReader.countRequests(Some(OK), Some(requestName))
			val koCount = totalCount - okCount
			val globalMinResponseTime = dataReader.minResponseTime(None, Some(requestName))
			val globalMaxResponseTime = dataReader.maxResponseTime(None, Some(requestName))
			val okMinResponseTime = dataReader.minResponseTime(Some(OK), Some(requestName))
			val okMaxResponseTime = dataReader.maxResponseTime(Some(OK), Some(requestName))
			val koMinResponseTime = dataReader.minResponseTime(Some(KO), Some(requestName))
			val koMaxResponseTime = dataReader.maxResponseTime(Some(KO), Some(requestName))

			val dataMillis = dataReader.requestRecordsGroupByExecutionStartDate(requestName)

			def responseTimeChartComponent = {
				val responseTimesSuccessData = responseTimeByMillisecondAsList(dataMillis, OK)
				val responseTimesFailuresData = responseTimeByMillisecondAsList(dataMillis, KO)
				val responseTimesSuccessSeries = new Series[Long, Long]("Response Time (success)", responseTimesSuccessData, List(BLUE))
				val responseTimesFailuresSeries = new Series[Long, Long]("Response Time (failure)", responseTimesFailuresData, List(RED))

				componentLibrary.getRequestDetailsResponseTimeChartComponent(responseTimesSuccessSeries, responseTimesFailuresSeries)
			}

			def responseTimeDistributionChartComponent = {
				val (okDistribution, koDistribution) = dataReader.responseTimeDistribution(100, Some(requestName))
				val okDistributionSeries = new Series[Long, Int]("Success", okDistribution, List(BLUE))
				val koDistributionSeries = new Series[Long, Int]("Failure", koDistribution, List(RED))

				componentLibrary.getRequestDetailsResponseTimeDistributionChartComponent(okDistributionSeries, koDistributionSeries)
			}

			def latencyChartComponent = {
				val latencySuccessData = latencyByMillisecondAsList(dataMillis, OK)
				val latencyFailuresData = latencyByMillisecondAsList(dataMillis, KO)

				val latencySuccessSeries = new Series[Long, Long]("Latency (success)", latencySuccessData, List(BLUE))
				val latencyFailuresSeries = new Series[Long, Long]("Latency (failure)", latencyFailuresData, List(RED))

				componentLibrary.getRequestDetailsLatencyChartComponent(latencySuccessSeries, latencyFailuresSeries)
			}

			def statisticsComponent = {
				val percent1 = configuration.chartingIndicatorsPercentile1 / 100.0
				val percent2 = configuration.chartingIndicatorsPercentile2 / 100.0

				val (globalPercentile1, globalPercentile2) = dataReader.percentiles(percent1, percent2, None, Some(requestName))
				val (successPercentile1, successPercentile2) = dataReader.percentiles(percent1, percent2, Some(OK), Some(requestName))
				val (failedPercentile1, failedPercentile2) = dataReader.percentiles(percent1, percent2, Some(KO), Some(requestName))

				val globalMeanResponseTime = dataReader.meanResponseTime(None, Some(requestName))
				val okMeanResponseTime = dataReader.meanResponseTime(Some(OK), Some(requestName))
				val koMeanResponseTime = dataReader.meanResponseTime(Some(KO), Some(requestName))
				val globalStandardDeviation = dataReader.responseTimeStandardDeviation(None, Some(requestName))
				val okStandardDeviation = dataReader.responseTimeStandardDeviation(Some(OK), Some(requestName))
				val koStandardDeviation = dataReader.responseTimeStandardDeviation(Some(KO), Some(requestName))

				val numberOfRequestsStatistics = new Statistics("numberOfRequests", totalCount, okCount, koCount)
				val minResponseTimeStatistics = new Statistics("min", globalMinResponseTime, okMinResponseTime, koMinResponseTime)
				val maxResponseTimeStatistics = new Statistics("max", globalMaxResponseTime, okMaxResponseTime, koMaxResponseTime)
				val meanStatistics = new Statistics("mean", globalMeanResponseTime, okMeanResponseTime, koMeanResponseTime)
				val stdDeviationStatistics = new Statistics("stdDeviation", globalStandardDeviation, okStandardDeviation, koStandardDeviation)
				val percentiles1 = new Statistics("percentiles1", globalPercentile1, successPercentile1, failedPercentile1)
				val percentiles2 = new Statistics("percentiles2", globalPercentile2, successPercentile2, failedPercentile2)

				new StatisticsTextComponent(numberOfRequestsStatistics, minResponseTimeStatistics, maxResponseTimeStatistics, meanStatistics, stdDeviationStatistics, percentiles1, percentiles2)
			}

			def scatterChartComponent = {
				val all = dataReader.numberOfEventsPerSecond((record: ChartRequestRecord) => record.executionStartDateNoMillis)
				val dataSeconds = dataReader.requestRecordsGroupByExecutionStartDate(requestName)
				val scatterPlotSuccessData = respTimeAgainstNbOfReqPerSecond(all, dataSeconds, OK)
				val scatterPlotFailuresData = respTimeAgainstNbOfReqPerSecond(all, dataSeconds, KO)
				val scatterPlotSuccessSeries = new Series[Int, Long]("Successes", scatterPlotSuccessData, List(TRANSLUCID_BLUE))
				val scatterPlotFailuresSeries = new Series[Int, Long]("Failures", scatterPlotFailuresData, List(TRANSLUCID_RED))

				componentLibrary.getRequestDetailsScatterChartComponent(scatterPlotSuccessSeries, scatterPlotFailuresSeries)
			}

			def indicatorChartComponent = {
				val indicatorsColumnData = dataReader.numberOfRequestInResponseTimeRange(configuration.chartingIndicatorsLowerBound, configuration.chartingIndicatorsHigherBound, Some(requestName))
				val indicatorsPieData = indicatorsColumnData.map { case (name, count) => name -> count * 100 / totalCount }
				val indicatorsColumnSeries = new Series[String, Int](EMPTY, indicatorsColumnData, List(GREEN, YELLOW, ORANGE, RED))
				val indicatorsPieSeries = new Series[String, Int](EMPTY, indicatorsPieData, List(GREEN, YELLOW, ORANGE, RED))

				componentLibrary.getRequestDetailsIndicatorChartComponent(indicatorsColumnSeries, indicatorsPieSeries)
			}

			// Create template
			val template =
				new RequestDetailsPageTemplate(requestName.substring(8),
					responseTimeChartComponent,
					responseTimeDistributionChartComponent,
					latencyChartComponent,
					statisticsComponent,
					scatterChartComponent,
					indicatorChartComponent)

			// Write template result to file
			new TemplateWriter(requestFile(runOn, requestName)).writeToFile(template.getOutput)
		}
	}
}