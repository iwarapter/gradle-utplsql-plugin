/*
 * Gradle Utplsql Plugin
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Iain Adams
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.iadams.gradle.plugins.utplsql.core

import groovy.sql.Sql
import groovy.time.TimeCategory
import groovy.time.TimeDuration
import java.sql.SQLException

/**
 * Class to run utplsql tests
 *
 * Created by Iain Adams on 09/09/2014.
 */
class UtplsqlRunner {

  File outputDir
  org.slf4j.Logger logger
  ReportGenerator reportGen
  Sql sql
  PackageTestResults results
  UtplsqlDAO dao

  def testsRun = 0
  def testsFailed = 0
  def testsErrors = 0

  UtplsqlRunner(File outputDir, org.slf4j.Logger logger, Sql conn) {
    outputDir.mkdirs()
    this.outputDir = outputDir
    this.logger = logger
    this.sql = conn
    this.dao = new UtplsqlDAO(sql)
    this.reportGen = new ReportGenerator()
  }

  /**
   * Run the utPLSQL tests in a single package. This method calls the relevant utPLSQL schema stored procedure and obtains the results, exporting
   * them in a Maven Surefire report.
   *
   * @param packageName
   * @param testMethod
   * @param setupMethod
   * @return
   * @throws SQLException
   */
  def runPackage(String packageName, String testMethod, boolean setupMethod) throws UtplsqlRunnerException {
    try {
      def packageStatus = dao.getPackageStatus(packageName)
      logger.debug "[DEBUG] Package $packageName is $packageStatus"
      switch (packageStatus) {
        case 'INVALID':
          results = reportGen.generateErrorReport()

          testsRun += results.testsRun
          testsFailed += results.testFailures
          testsErrors += results.testErrors

          return results.toXML(packageName, 0)

        case 'VALID':
          def start = new Date()

          def runId = dao.runUtplsqlProcedure(packageName, testMethod, setupMethod)
          logger.info "[INFO] RunID: $runId"

          def stop = new Date()
          TimeDuration td = TimeCategory.minus(stop, start)

          results = reportGen.generateReport(sql, runId)

          testsRun += results.testsRun
          testsFailed += results.testFailures
          testsErrors += results.testErrors

          return results.toXML(packageName, "${td.seconds}.${td.millis}".toFloat())

        case 'NO PACKAGE FOUND':
          results = new PackageTestResults()
          return results.toXML(packageName, 0)
      }
    }
    catch (SQLException e) {
      throw new UtplsqlRunnerException("Database communication error.", e)
    }
  }
}
