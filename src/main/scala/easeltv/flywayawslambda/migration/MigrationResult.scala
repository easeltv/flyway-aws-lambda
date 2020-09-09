package easeltv.flywayawslambda.migration

import java.util.Date

import org.flywaydb.core.api.{MigrationInfo => FlywayMigrationInfo}

case class MigrationResult(
  last_status: String,
  rdsUrl: String,
  appliedCount: Int,
  message: String,
  infos: Seq[MigrationInfo])
object MigrationResult {
  def success(rdsUrl: String, appliedCount: Int, infos: Seq[MigrationInfo]): MigrationResult = {
    MigrationResult("SUCCESS", rdsUrl, appliedCount, "Migration success", infos)
  }
  def failure(rdsUrl: String, cause: Throwable, infos: Seq[MigrationInfo]): MigrationResult = {
    MigrationResult("FAILURE", rdsUrl, 0, s"Migration failed by ${cause.toString}", infos)
  }
}

case class MigrationInfo(
  version: Option[String],
  `type`: Option[String],
  installedAt: Option[Date],
  state: Option[String],
  execTime: Option[Int],
  description: Option[String])
object MigrationInfo {
  def apply(i : FlywayMigrationInfo): MigrationInfo = {
    MigrationInfo(Option(i.getVersion).flatMap(v => Option(v.getVersion)), Option(i.getType).map(_.name), Option(i.getInstalledOn), Option(i.getState).map(_.name), Option(i.getExecutionTime), Option(i.getDescription))
  }
}
