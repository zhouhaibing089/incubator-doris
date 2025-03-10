// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.load.loadv2;


import org.apache.doris.analysis.BrokerDesc;
import org.apache.doris.analysis.DataDescription;
import org.apache.doris.analysis.LoadStmt;
import org.apache.doris.analysis.SqlParser;
import org.apache.doris.analysis.SqlScanner;
import org.apache.doris.catalog.AuthorizationInfo;
import org.apache.doris.catalog.Catalog;
import org.apache.doris.catalog.Database;
import org.apache.doris.catalog.OlapTable;
import org.apache.doris.catalog.Table;
import org.apache.doris.common.AnalysisException;
import org.apache.doris.common.Config;
import org.apache.doris.common.DdlException;
import org.apache.doris.common.FeMetaVersion;
import org.apache.doris.common.LabelAlreadyUsedException;
import org.apache.doris.common.MetaNotFoundException;
import org.apache.doris.common.UserException;
import org.apache.doris.common.io.Text;
import org.apache.doris.common.util.LogBuilder;
import org.apache.doris.common.util.LogKey;
import org.apache.doris.load.BrokerFileGroup;
import org.apache.doris.load.EtlJobType;
import org.apache.doris.load.FailMsg;
import org.apache.doris.load.PullLoadSourceInfo;
import org.apache.doris.service.FrontendOptions;
import org.apache.doris.thrift.TUniqueId;
import org.apache.doris.transaction.BeginTransactionException;
import org.apache.doris.transaction.TabletCommitInfo;
import org.apache.doris.transaction.TransactionState;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * There are 3 steps in BrokerLoadJob: BrokerPendingTask, LoadLoadingTask, CommitAndPublishTxn.
 * Step1: BrokerPendingTask will be created on method of unprotectedExecuteJob.
 * Step2: LoadLoadingTasks will be created by the method of onTaskFinished when BrokerPendingTask is finished.
 * Step3: CommitAndPublicTxn will be called by the method of onTaskFinished when all of LoadLoadingTasks are finished.
 */
public class BrokerLoadJob extends LoadJob {

    private static final Logger LOG = LogManager.getLogger(BrokerLoadJob.class);

    // input params
    private BrokerDesc brokerDesc;
    // this param is used to persist the expr of columns
    // the origin stmt is persisted instead of columns expr
    // the expr of columns will be reanalyze when the log is replayed
    private String originStmt = "";

    // include broker desc and data desc
    private PullLoadSourceInfo dataSourceInfo = new PullLoadSourceInfo();
    private List<TabletCommitInfo> commitInfos = Lists.newArrayList();

    // only for log replay
    public BrokerLoadJob() {
        super();
        this.jobType = EtlJobType.BROKER;
    }

    private BrokerLoadJob(long dbId, String label, BrokerDesc brokerDesc, String originStmt)
            throws MetaNotFoundException {
        super(dbId, label);
        this.timeoutSecond = Config.broker_load_default_timeout_second;
        this.brokerDesc = brokerDesc;
        this.originStmt = Strings.nullToEmpty(originStmt);
        this.jobType = EtlJobType.BROKER;
        this.authorizationInfo = gatherAuthInfo();
    }

    public static BrokerLoadJob fromLoadStmt(LoadStmt stmt, String originStmt) throws DdlException {
        // get db id
        String dbName = stmt.getLabel().getDbName();
        Database db = Catalog.getCurrentCatalog().getDb(stmt.getLabel().getDbName());
        if (db == null) {
            throw new DdlException("Database[" + dbName + "] does not exist");
        }

        // create job
        try {
            BrokerLoadJob brokerLoadJob = new BrokerLoadJob(db.getId(), stmt.getLabel().getLabelName(),
                    stmt.getBrokerDesc(), originStmt);
            brokerLoadJob.setJobProperties(stmt.getProperties());
            brokerLoadJob.checkAndSetDataSourceInfo(db, stmt.getDataDescriptions());
            return brokerLoadJob;
        } catch (MetaNotFoundException e) {
            throw new DdlException(e.getMessage());
        }
    }

    private void checkAndSetDataSourceInfo(Database db, List<DataDescription> dataDescriptions) throws DdlException {
        // check data source info
        db.readLock();
        try {
            for (DataDescription dataDescription : dataDescriptions) {
                BrokerFileGroup fileGroup = new BrokerFileGroup(dataDescription);
                fileGroup.parse(db, dataDescription);
                dataSourceInfo.addFileGroup(fileGroup);
            }
        } finally {
            db.readUnlock();
        }
    }

    private AuthorizationInfo gatherAuthInfo() throws MetaNotFoundException {
        Database database = Catalog.getCurrentCatalog().getDb(dbId);
        if (database == null) {
            throw new MetaNotFoundException("Database " + dbId + "has been deleted");
        }
        return new AuthorizationInfo(database.getFullName(), getTableNames());
    }

    @Override
    public Set<String> getTableNamesForShow() {
        Set<String> result = Sets.newHashSet();
        Database database = Catalog.getCurrentCatalog().getDb(dbId);
        if (database == null) {
            for (long tableId : dataSourceInfo.getIdToFileGroups().keySet()) {
                result.add(String.valueOf(tableId));
            }
            return result;
        }
        for (long tableId : dataSourceInfo.getIdToFileGroups().keySet()) {
            Table table = database.getTable(tableId);
            if (table == null) {
                result.add(String.valueOf(tableId));
            } else {
                result.add(table.getName());
            }
        }
        return result;
    }

    @Override
    public Set<String> getTableNames() throws MetaNotFoundException{
        Set<String> result = Sets.newHashSet();
        Database database = Catalog.getCurrentCatalog().getDb(dbId);
        if (database == null) {
            throw new MetaNotFoundException("Database " + dbId + "has been deleted");
        }
        // The database will not be locked in here.
        // The getTable is a thread-safe method called without read lock of database
        for (long tableId : dataSourceInfo.getIdToFileGroups().keySet()) {
            Table table = database.getTable(tableId);
            if (table == null) {
                throw new MetaNotFoundException("Failed to find table " + tableId + " in db " + dbId);
            } else {
                result.add(table.getName());
            }
        }
        return result;
    }

    @Override
    public void beginTxn() throws LabelAlreadyUsedException, BeginTransactionException, AnalysisException {
        transactionId = Catalog.getCurrentGlobalTransactionMgr()
                .beginTransaction(dbId, label, null, "FE: " + FrontendOptions.getLocalHostAddress(),
                                  TransactionState.LoadJobSourceType.BATCH_LOAD_JOB, id,
                                  timeoutSecond);
    }

    @Override
    protected void unprotectedExecuteJob() {
        LoadTask task = new BrokerLoadPendingTask(this, dataSourceInfo.getIdToFileGroups(), brokerDesc);
        idToTasks.put(task.getSignature(), task);
        Catalog.getCurrentCatalog().getLoadTaskScheduler().submit(task);
    }

    /**
     * Situation1: When attachment is instance of BrokerPendingTaskAttachment, this method is called by broker pending task.
     * LoadLoadingTask will be created after BrokerPendingTask is finished.
     * Situation2: When attachment is instance of BrokerLoadingTaskAttachment, this method is called by LoadLoadingTask.
     * CommitTxn will be called after all of LoadingTasks are finished.
     *
     * @param attachment
     */
    @Override
    public void onTaskFinished(TaskAttachment attachment) {
        if (attachment instanceof BrokerPendingTaskAttachment) {
            onPendingTaskFinished((BrokerPendingTaskAttachment) attachment);
        } else if (attachment instanceof BrokerLoadingTaskAttachment) {
            onLoadingTaskFinished((BrokerLoadingTaskAttachment) attachment);
        }
    }

    @Override
    public void onTaskFailed(long taskId, FailMsg failMsg) {
        writeLock();
        try {
            // check if job has been completed
            if (isCompleted()) {
                LOG.warn(new LogBuilder(LogKey.LOAD_JOB, id)
                                 .add("state", state)
                                 .add("error_msg", "this task will be ignored when job is completed")
                                 .build());
                return;
            }
            LoadTask loadTask = idToTasks.get(taskId);
            if (loadTask == null) {
                return;
            }
            if (loadTask.getRetryTime() <= 0) {
                unprotectedExecuteCancel(failMsg, true);
            } else {
                // retry task
                idToTasks.remove(loadTask.getSignature());
                if (loadTask instanceof LoadLoadingTask) {
                    loadStatistic.removeLoad(((LoadLoadingTask) loadTask).getLoadId());
                }
                loadTask.updateRetryInfo();
                idToTasks.put(loadTask.getSignature(), loadTask);
                // load id will be added to loadStatistic when executing this task
                Catalog.getCurrentCatalog().getLoadTaskScheduler().submit(loadTask);
                return;
            }
        } finally {
            writeUnlock();
        }

        logFinalOperation();
    }

    /**
     * If the db or table could not be found, the Broker load job will be cancelled.
     */
    @Override
    public void analyze() {
        if (Strings.isNullOrEmpty(originStmt)) {
            return;
        }
        // Reset dataSourceInfo, it will be re-created in analyze
        dataSourceInfo = new PullLoadSourceInfo();
        SqlParser parser = new SqlParser(new SqlScanner(new StringReader(originStmt)));
        LoadStmt stmt = null;
        try {
            stmt = (LoadStmt) parser.parse().value;
            for (DataDescription dataDescription : stmt.getDataDescriptions()) {
                dataDescription.analyzeWithoutCheckPriv();
            }
            Database db = Catalog.getCurrentCatalog().getDb(dbId);
            if (db == null) {
                throw new DdlException("Database[" + dbId + "] does not exist");
            }
            checkAndSetDataSourceInfo(db, stmt.getDataDescriptions());
        } catch (Exception e) {
            LOG.info(new LogBuilder(LogKey.LOAD_JOB, id)
                             .add("origin_stmt", originStmt)
                             .add("msg", "The failure happens in analyze, the load job will be cancelled with error:"
                                     + e.getMessage())
                             .build(), e);
            cancelJobWithoutCheck(new FailMsg(FailMsg.CancelType.LOAD_RUN_FAIL, e.getMessage()), false);
        }
    }

    /**
     * step1: divide job into loading task
     * step2: init the plan of task
     * step3: submit tasks into loadingTaskExecutor
     * @param attachment BrokerPendingTaskAttachment
     */
    private void onPendingTaskFinished(BrokerPendingTaskAttachment attachment) {
        writeLock();
        try {
            // check if job has been cancelled
            if (isCompleted()) {
                LOG.warn(new LogBuilder(LogKey.LOAD_JOB, id)
                                 .add("state", state)
                                 .add("error_msg", "this task will be ignored when job is completed")
                                 .build());
                return;
            }
            if (finishedTaskIds.contains(attachment.getTaskId())) {
                LOG.warn(new LogBuilder(LogKey.LOAD_JOB, id)
                                 .add("task_id", attachment.getTaskId())
                                 .add("error_msg", "this is a duplicated callback of pending task "
                                         + "when broker already has loading task")
                                 .build());
                return;
            }

            // add task id into finishedTaskIds
            finishedTaskIds.add(attachment.getTaskId());
        } finally {
            writeUnlock();
        }

        try {
            Database db = getDb();
            createLoadingTask(db, attachment);
        } catch (UserException e) {
            LOG.warn(new LogBuilder(LogKey.LOAD_JOB, id)
                             .add("database_id", dbId)
                             .add("error_msg", "Failed to divide job into loading task.")
                             .build(), e);
            cancelJobWithoutCheck(new FailMsg(FailMsg.CancelType.ETL_RUN_FAIL, e.getMessage()), true);
            return;
        }

        loadStartTimestamp = System.currentTimeMillis();
    }

    private void createLoadingTask(Database db, BrokerPendingTaskAttachment attachment) throws UserException {
        // divide job into broker loading task by table
        db.readLock();
        try {
            List<LoadLoadingTask> newLoadingTasks = Lists.newArrayList();
            for (Map.Entry<Long, List<BrokerFileGroup>> entry :
                    dataSourceInfo.getIdToFileGroups().entrySet()) {
                long tableId = entry.getKey();
                OlapTable table = (OlapTable) db.getTable(tableId);
                if (table == null) {
                    LOG.warn(new LogBuilder(LogKey.LOAD_JOB, id)
                                     .add("database_id", dbId)
                                     .add("table_id", tableId)
                                     .add("error_msg", "Failed to divide job into loading task when table not found")
                                     .build());
                    throw new MetaNotFoundException("Failed to divide job into loading task when table "
                                                            + tableId + " not found");
                }

                // Generate loading task and init the plan of task
                LoadLoadingTask task = new LoadLoadingTask(db, table, brokerDesc,
                        entry.getValue(), getDeadlineMs(), execMemLimit,
                        strictMode, transactionId, this, timezone);
                UUID uuid = UUID.randomUUID();
                TUniqueId loadId = new TUniqueId(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
                task.init(loadId, attachment.getFileStatusByTable(tableId), attachment.getFileNumByTable(tableId));
                idToTasks.put(task.getSignature(), task);
                // idToTasks contains previous LoadPendingTasks, so idToTasks is just used to save all tasks.
                // use newLoadingTasks to save new created loading tasks and submit them later.
                newLoadingTasks.add(task);
                // load id will be added to loadStatistic when executing this task

                // save all related tables and rollups in transaction state
                TransactionState txnState = Catalog.getCurrentGlobalTransactionMgr().getTransactionState(transactionId);
                if (txnState == null) {
                    throw new UserException("txn does not exist: " + transactionId);
                }
                txnState.addTableIndexes(table);
            }
            // submit all tasks together
            for (LoadTask loadTask : newLoadingTasks) {
                Catalog.getCurrentCatalog().getLoadTaskScheduler().submit(loadTask);
            }
        } finally {
            db.readUnlock();
        }
    }

    private void onLoadingTaskFinished(BrokerLoadingTaskAttachment attachment) {
        writeLock();
        try {
            // check if job has been cancelled
            if (isCompleted()) {
                LOG.warn(new LogBuilder(LogKey.LOAD_JOB, id)
                                 .add("state", state)
                                 .add("error_msg", "this task will be ignored when job is completed")
                                 .build());
                return;
            }

            // check if task has been finished
            if (finishedTaskIds.contains(attachment.getTaskId())) {
                LOG.warn(new LogBuilder(LogKey.LOAD_JOB, id)
                                 .add("task_id", attachment.getTaskId())
                                 .add("error_msg", "this is a duplicated callback of loading task").build());
                return;
            }

            // update loading status
            finishedTaskIds.add(attachment.getTaskId());
            updateLoadingStatus(attachment);

            // begin commit txn when all of loading tasks have been finished
            if (finishedTaskIds.size() != idToTasks.size()) {
                return;
            }
        } finally {
            writeUnlock();
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(new LogBuilder(LogKey.LOAD_JOB, id)
                              .add("commit_infos", Joiner.on(",").join(commitInfos))
                              .build());
        }

        // check data quality
        if (!checkDataQuality()) {
            cancelJobWithoutCheck(new FailMsg(FailMsg.CancelType.ETL_QUALITY_UNSATISFIED, QUALITY_FAIL_MSG),true);
            return;
        }
        Database db = null;
        try {
            db = getDb();
        } catch (MetaNotFoundException e) {
            LOG.warn(new LogBuilder(LogKey.LOAD_JOB, id)
                             .add("database_id", dbId)
                             .add("error_msg", "db has been deleted when job is loading")
                             .build(), e);
            cancelJobWithoutCheck(new FailMsg(FailMsg.CancelType.LOAD_RUN_FAIL, e.getMessage()), true);
        }
        db.writeLock();
        try {
            LOG.info(new LogBuilder(LogKey.LOAD_JOB, id)
                             .add("txn_id", transactionId)
                             .add("msg", "Load job try to commit txn")
                             .build());
            Catalog.getCurrentGlobalTransactionMgr().commitTransaction(
                    dbId, transactionId, commitInfos,
                    new LoadJobFinalOperation(id, loadingStatus, progress, loadStartTimestamp,
                                              finishTimestamp, state, failMsg));
        } catch (UserException e) {
            LOG.warn(new LogBuilder(LogKey.LOAD_JOB, id)
                             .add("database_id", dbId)
                             .add("error_msg", "Failed to commit txn with error:" + e.getMessage())
                             .build(), e);
            cancelJobWithoutCheck(new FailMsg(FailMsg.CancelType.LOAD_RUN_FAIL, e.getMessage()),true);
            return;
        } finally {
            db.writeUnlock();
        }
    }

    private void updateLoadingStatus(BrokerLoadingTaskAttachment attachment) {
        loadingStatus.replaceCounter(DPP_ABNORMAL_ALL,
                                     increaseCounter(DPP_ABNORMAL_ALL, attachment.getCounter(DPP_ABNORMAL_ALL)));
        loadingStatus.replaceCounter(DPP_NORMAL_ALL,
                                     increaseCounter(DPP_NORMAL_ALL, attachment.getCounter(DPP_NORMAL_ALL)));
        loadingStatus.replaceCounter(UNSELECTED_ROWS,
                                     increaseCounter(UNSELECTED_ROWS, attachment.getCounter(UNSELECTED_ROWS)));
        if (attachment.getTrackingUrl() != null) {
            loadingStatus.setTrackingUrl(attachment.getTrackingUrl());
        }
        commitInfos.addAll(attachment.getCommitInfoList());
        progress = (int) ((double) finishedTaskIds.size() / idToTasks.size() * 100);
        if (progress == 100) {
            progress = 99;
        }
    }

    private String increaseCounter(String key, String deltaValue) {
        long value = 0;
        if (loadingStatus.getCounters().containsKey(key)) {
            value = Long.valueOf(loadingStatus.getCounters().get(key));
        }
        if (deltaValue != null) {
            value += Long.valueOf(deltaValue);
        }
        return String.valueOf(value);
    }

    @Override
    protected void replayTxnAttachment(TransactionState txnState) {
        if (txnState.getTxnCommitAttachment() == null) {
            // The txn attachment maybe null when broker load has been cancelled without attachment.
            // The end log of broker load has been record but the callback id of txnState hasn't been removed
            // So the callback of txn is executed when log of txn aborted is replayed.
            return;
        }
        unprotectReadEndOperation((LoadJobFinalOperation) txnState.getTxnCommitAttachment());
    }

    @Override
    public void write(DataOutput out) throws IOException {
        super.write(out);
        brokerDesc.write(out);
        Text.writeString(out, originStmt);
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        super.readFields(in);
        brokerDesc = BrokerDesc.read(in);
        if (Catalog.getCurrentCatalogJournalVersion() < FeMetaVersion.VERSION_61) {
            dataSourceInfo.readFields(in);
        }

        if (Catalog.getCurrentCatalogJournalVersion() >= FeMetaVersion.VERSION_58) {
            originStmt = Text.readString(in);
        } else {
            originStmt = "";
        }
        // The origin stmt does not be analyzed in here.
        // The reason is that it will thrown MetaNotFoundException when the tableId could not be found by tableName.
        // The origin stmt will be analyzed after the replay is completed.
    }

}
