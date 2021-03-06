/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.gearpump.cluster

import akka.actor.ActorRef
import org.apache.gearpump.cluster.master.Master.{MasterInfo, MasterDescription}
import org.apache.gearpump.cluster.scheduler.{Resource, ResourceAllocation, ResourceRequest}
import org.apache.gearpump.cluster.worker.WorkerDescription

import scala.util.Try

/**
 * Cluster Bootup Flow
 */
object WorkerToMaster {
  case object RegisterNewWorker
  case class RegisterWorker(workerId: Int)
  case class ResourceUpdate(worker: ActorRef, workerId: Int, resource: Resource)
}

object MasterToWorker {
  case class WorkerRegistered(workerId : Int, masterInfo: MasterInfo)
  case class UpdateResourceFailed(reason : String = null, ex: Throwable = null)
  case object UpdateResourceSucceed
}

/**
 * Application Flow
 */

object ClientToMaster {
  case class SubmitApplication(appDescription: Application, appJar: Option[AppJar], username : String = System.getProperty("user.name"))
  case class ShutdownApplication(appId: Int)
  case class ResolveAppId(appId: Int)

  case object GetJarFileContainer
}

object MasterToClient {
  case class SubmitApplicationResult(appId : Try[Int])
  case class ShutdownApplicationResult(appId : Try[Int])
  case class ReplayApplicationResult(appId: Try[Int])
  case class ResolveAppIdResult(appMaster: Try[ActorRef])
}

trait AppMasterRegisterData

object AppMasterToMaster {
  case class RegisterAppMaster(appMaster: ActorRef, registerData : AppMasterRegisterData)
  case class InvalidAppMaster(appId: Int, appMaster: String, reason: Throwable)
  case class RequestResource(appId: Int, request: ResourceRequest)

  case class SaveAppData(appId: Int, key: String, value: Any)
  case object AppDataSaved
  case object SaveAppDataFailed

  case class GetAppData(appId: Int, key: String)
  case class GetAppDataResult(key: String, value: Any)
  case class AppMasterDataDetail(appId: Int, application: Application)

  case object GetAllWorkers
  case class GetWorkerData(workerId: Int)
  case class WorkerData(workerDescription: Option[WorkerDescription])

  case object GetMasterData
  case class MasterData(masterDescripton: MasterDescription)
}

object MasterToAppMaster {
  case class ResourceAllocated(allocations: Array[ResourceAllocation]){
    override def equals(other: Any): Boolean = {
      other match {
        case that: ResourceAllocated =>
          allocations.sortBy(_.workerId).sameElements(that.allocations.sortBy(_.workerId))
        case _ =>
          false
      }
    }
  }
  case class AppMasterRegistered(appId: Int)
  case object ShutdownAppMaster
  case class AppMasterData(appId: Int, workerPath: String)
  case class AppMasterDataRequest(appId: Int, detail: Boolean = false)
  case class AppMastersData(appMasters: List[AppMasterData])
  case object AppMastersDataRequest
  case class AppMasterDataDetailRequest(appId: Int)

  case class ReplayFromTimestampWindowTrailingEdge(appId: Int)

  case class WorkerList(workers: List[Int])
}

object AppMasterToWorker {
  case class LaunchExecutor(appId: Int, executorId: Int, resource: Resource, executorJvmConfig: ExecutorJVMConfig)
  case class ShutdownExecutor(appId : Int, executorId : Int, reason : String)
}

object WorkerToAppMaster {
  case class ExecutorLaunchRejected(reason: String = null, ex: Throwable = null)
  case class ShutdownExecutorSucceed(appId: Int, executorId: Int)
  case class ShutdownExecutorFailed(reason: String = null, ex: Throwable = null)
}

