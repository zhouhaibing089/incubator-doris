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

package org.apache.doris.common;

public class Config extends ConfigBase {
    
    /*
     * The max size of one sys log and audit log
     */
    @ConfField public static int log_roll_size_mb = 1024; // 1 GB

    /*
     * sys_log_dir:
     *      This specifies FE log dir. FE will produces 2 log files:
     *      fe.log:      all logs of FE process.
     *      fe.warn.log  all WARNING and ERROR log of FE process.
     *      
     * sys_log_level:
     *      INFO, WARNING, ERROR, FATAL
     *      
     * sys_log_roll_num:
     *      Maximal FE log files to be kept.
     *      
     * sys_log_verbose_modules:
     *      Verbose modules. VERBOSE level is implemented by log4j DEBUG level.
     *      eg:
     *          sys_log_verbose_modules = org.apache.doris.catalog
     *      This will only print debug log of files in package org.apache.doris.catalog and all its sub packages.
     *      
     * sys_log_roll_interval:
     *      DAY:  log suffix is yyyyMMdd
     *      HOUR: log suffix is yyyyMMddHH
     */
    @ConfField public static String sys_log_dir = System.getenv("DORIS_HOME") + "/log";
    @ConfField public static String sys_log_level = "INFO"; 
    @ConfField public static int sys_log_roll_num = 10;
    @ConfField public static String[] sys_log_verbose_modules = {};
    @ConfField public static String sys_log_roll_interval = "DAY";
    @Deprecated
    @ConfField public static String sys_log_roll_mode = "SIZE-MB-1024";

    /*
     * audit_log_dir:
     *      This specifies FE audit log dir.
     *      Audit log fe.audit.log contains all requests with related infos such as user, host, cost, status, etc.
     * 
     * audit_log_roll_num:
     *      Maximal FE audit log files to be kept.
     *      
     * audit_log_modules:
     *       Slow query contains all queries which cost exceed *qe_slow_log_ms*
     *       
     * qe_slow_log_ms:
     *      If the response time of a query exceed this threshold, it will be recored in audit log as slow_query.
     *      
     * audit_log_roll_interval:
     *      DAY:  log suffix is yyyyMMdd
     *      HOUR: log suffix is yyyyMMddHH
     */
    @ConfField public static String audit_log_dir = System.getenv("DORIS_HOME") + "/log";
    @ConfField public static int audit_log_roll_num = 90; // nearly 3 months
    @ConfField public static String[] audit_log_modules = {"slow_query", "query"};
    @ConfField(mutable = true) public static long qe_slow_log_ms = 5000;
    @ConfField public static String audit_log_roll_interval = "DAY";
    @Deprecated
    @ConfField public static String audit_log_roll_mode = "TIME-DAY";

    /*
     * Labels of finished or cancelled load jobs will be removed after *label_keep_max_second*
     * The removed labels can be reused.
     * Set a short time will lower the FE memory usage.
     * (Because all load jobs' info is kept in memory before being removed)
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int label_keep_max_second = 3 * 24 * 3600; // 3 days
    /*
     * The max keep time of some kind of jobs.
     * like schema change job and rollup job.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int history_job_keep_max_second = 7 * 24 * 3600; // 7 days
    /*
     * Load label cleaner will run every *label_clean_interval_second* to clean the outdated jobs.
     */
    @ConfField public static int label_clean_interval_second = 4 * 3600; // 4 hours
    /*
     * the transaction will be cleaned after transaction_clean_interval_second seconds if the transaction is visible or aborted
     */
    @ConfField public static int transaction_clean_interval_second = 1800; // 0.5 hours

    // Configurations for meta data durability
    /*
     * Palo meta data will be saved here.
     * The storage of this dir is highly recommended as to be:
     * 1. High write performance (SSD)
     * 2. Safe (RAID)
     */
    @ConfField public static String meta_dir = System.getenv("DORIS_HOME") + "/palo-meta";
    
    /*
     * temp dir is used to save intermediate results of some process, such as backup and restore process.
     * file in this dir will be cleaned after these process is finished.
     */
    @ConfField public static String tmp_dir = System.getenv("DORIS_HOME") + "/temp_dir";
    
    /*
     * Edit log type.
     * BDB: write log to bdbje
     * LOCAL: deprecated.
     */
    @ConfField
    public static String edit_log_type = "BDB";
    /*
     * bdbje port
     */
    @ConfField
    public static int edit_log_port = 9010;
    /*
     * Master FE will save image every *edit_log_roll_num* meta journals.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int edit_log_roll_num = 50000;
    /*
     * Non-master FE will stop offering service
     * if meta data delay gap exceeds *meta_delay_toleration_second*
     */
    @ConfField public static int meta_delay_toleration_second = 300;    // 5 min
    /*
     * Master FE sync policy of bdbje.
     * If you only deploy one Follower FE, set this to 'SYNC'. If you deploy more than 3 Follower FE,
     * you can set this and the following 'replica_sync_policy' to WRITE_NO_SYNC.
     * more info, see: http://docs.oracle.com/cd/E17277_02/html/java/com/sleepycat/je/Durability.SyncPolicy.html
     */
    @ConfField public static String master_sync_policy = "SYNC"; // SYNC, NO_SYNC, WRITE_NO_SYNC
    /*
     * Follower FE sync policy of bdbje.
     */
    @ConfField public static String replica_sync_policy = "SYNC"; // SYNC, NO_SYNC, WRITE_NO_SYNC
    /*
     * Replica ack policy of bdbje.
     * more info, see: http://docs.oracle.com/cd/E17277_02/html/java/com/sleepycat/je/Durability.ReplicaAckPolicy.html
     */
    @ConfField public static String replica_ack_policy = "SIMPLE_MAJORITY"; // ALL, NONE, SIMPLE_MAJORITY
    
    /*
     * the max txn number which bdbje can rollback when trying to rejoin the group
     */
    @ConfField public static int txn_rollback_limit = 100;

    /*
     * Specified an IP for frontend, instead of the ip get by *InetAddress.getByName*.
     * This can be used when *InetAddress.getByName* get an unexpected IP address.
     * Default is "0.0.0.0", which means not set.
     * CAN NOT set this as a hostname, only IP.
     */
    @ConfField public static String frontend_address = "0.0.0.0";

    /*
     * Declare a selection strategy for those servers have many ips.
     * Note that there should at most one ip match this list.
     * this is a list in semicolon-delimited format, in CIDR notation, e.g. 10.10.10.0/24
     * If no ip match this rule, will choose one randomly.
     */
    @ConfField public static String priority_networks = "";

    /*
     * Kudu is currently not supported.
     */
    @ConfField public static String kudu_master_addresses = "127.0.0.1:8030";
    @ConfField public static int kudu_client_timeout_ms = 500;

    /*
     * If true, FE will reset bdbje replication group(that is, to remove all electable nodes info)
     * and is supposed to start as Master.
     * If all the electable nodes can not start, we can copy the meta data
     * to another node and set this config to true to try to restart the FE.
     */
    @ConfField public static String metadata_failure_recovery = "false";

    /*
     * If true, non-master FE will ignore the meta data delay gap between Master FE and its self,
     * even if the metadata delay gap exceeds *meta_delay_toleration_second*.
     * Non-master FE will still offer read service.
     *
     * This is helpful when you try to stop the Master FE for a relatively long time for some reason,
     * but still wish the non-master FE can offer read service.
     */
    @ConfField(mutable = true)
    public static boolean ignore_meta_check = false;

    /*
     * Set the maximum acceptable clock skew between non-master FE to Master FE host.
     * This value is checked whenever a non-master FE establishes a connection to master FE via BDBJE.
     * The connection is abandoned if the clock skew is larger than this value.
     */
    @ConfField public static long max_bdbje_clock_delta_ms = 5000; // 5s

    /*
     * Fe http port
     * Currently, all FEs' http port must be same.
     */
    @ConfField public static int http_port = 8030;

    /*
     * The backlog_num for netty http server
     * When you enlarge this backlog_num, you should ensure it's value larger than
     * the linux /proc/sys/net/core/somaxconn config
     */
    @ConfField public static int http_backlog_num = 1024;

    /*
     * The backlog_num for thrift server
     * When you enlarge this backlog_num, you should ensure it's value larger than
     * the linux /proc/sys/net/core/somaxconn config
     */
    @ConfField public static int thrift_backlog_num = 1024;

    /*
     * FE thrift server port
     */
    @ConfField public static int rpc_port = 9020;
    /*
     * FE mysql server port
     */
    @ConfField public static int query_port = 9030;

    /*
     * Cluster name will be shown as the title of web page
     */
    @ConfField public static String cluster_name = "Baidu Palo";
    /*
     * node(FE or BE) will be considered belonging to the same Palo cluster if they have same cluster id.
     * Cluster id is usually a random integer generated when master FE start at first time.
     * You can also sepecify one.
     */
    @ConfField public static int cluster_id = -1;
    /*
     * Cluster token used for internal authentication.
     */
    @ConfField public static String auth_token = "";

    // Configurations for load, clone, create table, alter table etc. We will rarely change them
    /*
     * Maximal waiting time for creating a single replica.
     * eg.
     *      if you create a table with #m tablets and #n replicas for each tablet,
     *      the create table request will run at most (m * n * tablet_create_timeout_second) before timeout.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int tablet_create_timeout_second = 1;
    /*
     * In order not to wait too long for create table(index), set a max timeout.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int max_create_table_timeout_second = 60;
    
    /*
     * Maximal waiting time for all publish version tasks of one transaction to be finished
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int publish_version_timeout_second = 30; // 30 seconds
    
    /*
     * minimal intervals between two publish version action
     */
    @ConfField public static int publish_version_interval_ms = 100;

    /*
     * Maximal wait seconds for straggler node in load
     * eg.
     *      there are 3 replicas A, B, C
     *      load is already quorum finished(A,B) at t1 and C is not finished
     *      if (current_time - t1) > 300s, then palo will treat C as a failure node
     *      will call transaction manager to commit the transaction and tell transaction manager 
     *      that C is failed
     * 
     * This is also used when waiting for publish tasks
     * 
     * TODO this parameter is the default value for all job and the DBA could specify it for separate job
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int load_straggler_wait_second = 300;
    
    /*
     * Maximal memory layout length of a row. default is 100 KB.
     * In BE, the maximal size of a RowBlock is 100MB(Configure as max_unpacked_row_block_size in be.conf).
     * And each RowBlock contains 1024 rows. So the maximal size of a row is approximately 100 KB.
     * 
     * eg.
     *      schema: k1(int), v1(decimal), v2(varchar(2000))
     *      then the memory layout length of a row is: 8(int) + 40(decimal) + 2000(varchar) = 2048 (Bytes)
     *      
     * See memory layout length of all types, run 'help create table' in mysql-client.
     * 
     * If you want to increase this number to support more columns in a row, you also need to increase the 
     * max_unpacked_row_block_size in be.conf. But the performance impact is unknown.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int max_layout_length_per_row = 100000; // 100k

    /*
     * Load checker's running interval.
     * A load job will transfer its state from PENDING to ETL to LOADING to FINISHED.
     * So a load job will cost at least 3 check intervals to finish.
     */
    @ConfField public static int load_checker_interval_second = 5;

    /*
     * Concurrency of HIGH priority pending load jobs.
     * Load job priority is defined as HIGH or NORMAL.
     * All mini batch load jobs are HIGH priority, other types of load jobs are NORMAL priority.
     * Priority is set to avoid that a slow load job occupies a thread for a long time.
     * This is just a internal optimized scheduling policy.
     * Currently, you can not specified the job priority manually,
     * and do not change this if you know what you are doing.
     */
    @ConfField public static int load_pending_thread_num_high_priority = 3;
    /*
     * Concurrency of NORMAL priority pending load jobs.
     * Do not change this if you know what you are doing.
     */
    @ConfField public static int load_pending_thread_num_normal_priority = 10;
    /*
     * Concurrency of HIGH priority etl load jobs.
     * Do not change this if you know what you are doing.
     */
    @ConfField public static int load_etl_thread_num_high_priority = 3;
    /*
     * Concurrency of NORMAL priority etl load jobs.
     * Do not change this if you know what you are doing.
     */
    @ConfField public static int load_etl_thread_num_normal_priority = 10;
    /*
     * Not available.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int load_input_size_limit_gb = 0; // GB, 0 is no limit
    /*
     * Not available.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int load_running_job_num_limit = 0; // 0 is no limit
    /*
     * Default broker load timeout
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int broker_load_default_timeout_second = 14400; // 4 hour

    /*
     * Default non-streaming mini load timeout
     */
    @Deprecated
    @ConfField(mutable = true, masterOnly = true)
    public static int mini_load_default_timeout_second = 3600; // 1 hour
    
    /*
     * Default insert load timeout
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int insert_load_default_timeout_second = 3600; // 1 hour
    
    /*
     * Default stream load and streaming mini load timeout
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int stream_load_default_timeout_second = 600; // 600s

    /*
     * Max load timeout applicable to all type of load
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int max_load_timeout_second = 259200; // 3days

    /*
    * Min stream load timeout applicable to all type of load
    */
    @ConfField(mutable = true, masterOnly = true)
    public static int min_load_timeout_second = 1; // 1s

    /*
     * Default hadoop load timeout
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int hadoop_load_default_timeout_second = 86400 * 3; // 3 day

    /*
     * Default number of waiting jobs for routine load and version 2 of load
     * This is a desired number.
     * In some situation, such as switch the master, the current number is maybe more then desired_max_waiting_jobs
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int desired_max_waiting_jobs = 100;

    /*
     * maximun concurrent running txn num including prepare, commit txns under a single db
     * txn manager will reject coming txns
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int max_running_txn_num_per_db = 100;

    /*
     * The load task executor pool size. This pool size limits the max running load tasks.
     * Currently, it only limits the load task of broker load, pending and loading phases.
     * It should be less than 'max_running_txn_num_per_db'
     */
    public static int async_load_task_pool_size = 10;

    /*
     * Same meaning as *tablet_create_timeout_second*, but used when delete a tablet.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int tablet_delete_timeout_second = 2;
    /*
     * Clone checker's running interval.
     */
    @ConfField public static int clone_checker_interval_second = 300;
    /*
     * Default timeout of a single clone job. Set long enough to fit your replica size.
     * The larger the replica data size is, the more time is will cost to finish clone.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int clone_job_timeout_second = 7200; // 2h
    /*
     * Concurrency of LOW priority clone jobs.
     * Concurrency of High priority clone jobs is currently unlimit.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int clone_max_job_num = 100;
    /*
     * LOW priority clone job's delay trigger time.
     * A clone job contains a tablet which need to be cloned(recovery or migration).
     * If the priority is LOW, it will be delayed *clone_low_priority_delay_second*
     * after the job creation and then be executed.
     * This is to avoid a large number of clone jobs running at same time only because a host is down for a short time.
     *
     * NOTICE that this config(and *clone_normal_priority_delay_second* as well)
     * will not work if it's smaller then *clone_checker_interval_second*
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int clone_low_priority_delay_second = 600;
    /*
     * NORMAL priority clone job's delay trigger time.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int clone_normal_priority_delay_second = 300;
    /*
     * HIGH priority clone job's delay trigger time.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int clone_high_priority_delay_second = 0;
    /*
     * the minimal delay seconds between a replica is failed and fe try to recovery it using clone.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int replica_delay_recovery_second = 0;
    /*
     * Balance threshold of data size in BE.
     * The balance algorithm is:
     * 1. Calculate the average used capacity(AUC) of the entire cluster. (total data size / total backends num)
     * 2. The high water level is (AUC * (1 + clone_capacity_balance_threshold))
     * 3. The low water level is (AUC * (1 - clone_capacity_balance_threshold))
     * The Clone checker will try to move replica from high water level BE to low water level BE.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static double clone_capacity_balance_threshold = 0.2;
    /*
     * Balance threshold of num of replicas in Backends.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static double clone_distribution_balance_threshold = 0.2;
    /*
     * The high water of disk capacity used percent.
     * This is used for calculating load score of a backend.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static double capacity_used_percent_high_water = 0.75;
    /*
     * Maximal timeout of ALTER TABLE request. Set long enough to fit your table data size.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int alter_table_timeout_second = 86400; // 1day
    /*
     * If a backend is down for *max_backend_down_time_second*, a BACKEND_DOWN event will be triggered.
     * Do not set this if you know what you are doing.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int max_backend_down_time_second = 3600; // 1h
    /*
     * When create a table(or partition), you can specify its storage media(HDD or SSD).
     * If set to SSD, this specifies the default duration that tablets will stay on SSD.
     * After that, tablets will be moved to HDD automatically.
     * You can set storage cooldown time in CREATE TABLE stmt.
     */
    @ConfField public static long storage_cooldown_second = 30 * 24 * 3600L; // 30 days
    /*
     * After dropping database(table/partition), you can recover it by using RECOVER stmt.
     * And this specifies the maximal data retention time. After time, the data will be deleted permanently.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static long catalog_trash_expire_second = 86400L; // 1day
    /*
     * Maximal bytes that a single broker scanner will read.
     * Do not set this if you know what you are doing.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static long min_bytes_per_broker_scanner = 67108864L; // 64MB
    /*
     * Maximal concurrency of broker scanners.
     * Do not set this if you know what you are doing.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int max_broker_concurrency = 10;

    /*
     * Export checker's running interval.
     */
    @ConfField public static int export_checker_interval_second = 5;
    /*
     * Limitation of the concurrency of running export jobs.
     * Default is 5.
     * 0 is unlimited
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int export_running_job_num_limit = 5;
    /*
     * Default timeout of export jobs.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int export_task_default_timeout_second = 2 * 3600; // 2h
    /*
     * Number of tablets per export query plan
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int export_tablet_num_per_task = 5;

    // Configurations for consistency check
    /*
     * Consistency checker will run from *consistency_check_start_time* to *consistency_check_end_time*.
     * Default is from 23:00 to 04:00
     */
    @ConfField(mutable = true, masterOnly = true)
    public static String consistency_check_start_time = "23";
    @ConfField(mutable = true, masterOnly = true)
    public static String consistency_check_end_time = "4";
    /*
     * Default timeout of a single consistency check task. Set long enough to fit your tablet size.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static long check_consistency_default_timeout_second = 600; // 10 min

    // Configurations for query engine
    /*
     * Maximal number of connections per FE.
     */
    @ConfField public static int qe_max_connection = 1024;
    /*
     * Maximal number of connections per user, per FE.
     */
    @ConfField public static int max_conn_per_user = 100;

    /*
    * The memory_limit for colocote join PlanFragment instance =
    * exec_mem_limit / min (query_colocate_join_memory_limit_penalty_factor, instance_num)
    */
    @ConfField(mutable = true)
    public static int query_colocate_join_memory_limit_penalty_factor = 8;

    /*
     * Deprecated after 0.10
     */
    @ConfField
    public static boolean disable_colocate_join = false;
    /*
     * The default user resource publishing timeout.
     */
    @ConfField public static int meta_publish_timeout_ms = 1000;
    @ConfField public static boolean proxy_auth_enable = false;
    @ConfField public static String proxy_auth_magic_prefix = "x@8";
    /*
     * Limit on the number of expr children of an expr tree.
     * Exceed this limit may cause long analysis time while holding database read lock.
     * Do not set this if you know what you are doing.
     */
    @ConfField(mutable = true)
    public static int expr_children_limit = 10000;
    /*
     * Limit on the depth of an expr tree.
     * Exceed this limit may cause long analysis time while holding db read lock.
     * Do not set this if you know what you are doing.
     */
    @ConfField(mutable = true)
    public static int expr_depth_limit = 3000;

    // Configurations for backup and restore
    /*
     * Plugins' path for BACKUP and RESTORE operations. Currently deprecated.
     */
    @Deprecated
    @ConfField public static String backup_plugin_path = "/tools/trans_file_tool/trans_files.sh";

    // Configurations for hadoop dpp
    /*
     * The following configurations are not available.
     */
    @ConfField public static String dpp_hadoop_client_path = "/lib/hadoop-client/hadoop/bin/hadoop";
    @ConfField public static long dpp_bytes_per_reduce = 100 * 1024 * 1024L; // 100M
    @ConfField public static String dpp_default_cluster = "palo-dpp";
    @ConfField public static String dpp_default_config_str = ""
            + "{"
            + "hadoop_configs : '"
            + "mapred.job.priority=NORMAL;"
            + "mapred.job.map.capacity=50;"
            + "mapred.job.reduce.capacity=50;"
            + "mapred.hce.replace.streaming=false;"
            + "abaci.long.stored.job=true;"
            + "dce.shuffle.enable=false;"
            + "dfs.client.authserver.force_stop=true;"
            + "dfs.client.auth.method=0"
            + "'}";
    @ConfField public static String dpp_config_str = ""
            + "{palo-dpp : {"
            + "hadoop_palo_path : '/dir',"
            + "hadoop_configs : '"
            + "fs.default.name=hdfs://host:port;"
            + "mapred.job.tracker=host:port;"
            + "hadoop.job.ugi=user,password"
            + "'}"
            + "}";

    // For forward compatibility, will be removed later.
    // check token when download image file.
    @ConfField public static boolean enable_token_check = true;

    /*
     * Set to true if you deploy Palo using thirdparty deploy manager
     * Valid options are:
     *      disable:    no deploy manager
     *      k8s:        Kubernetes
     *      ambari:     Ambari
     *      local:      Local File (for test or Boxer2 BCC version)
     */
    @ConfField public static String enable_deploy_manager = "disable";
    
    // If use k8s deploy manager locally, set this to true and prepare the certs files
    @ConfField public static boolean with_k8s_certs = false;
    
    // Set runtime locale when exec some cmds
    @ConfField public static String locale = "zh_CN.UTF-8";

    // default timeout of backup job
    @ConfField(mutable = true, masterOnly = true)
    public static int backup_job_default_timeout_ms = 86400 * 1000; // 1 day
    
    /*
     * 'storage_high_watermark_usage_percent' limit the max capacity usage percent of a Backend storage path.
     * 'storage_min_left_capacity_bytes' limit the minimum left capacity of a Backend storage path.
     * If both limitations are reached, this storage path can not be chose as tablet balance destination.
     * But for tablet recovery, we may exceed these limit for keeping data integrity as much as possible.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int storage_high_watermark_usage_percent = 85;
    @ConfField(mutable = true, masterOnly = true)
    public static long storage_min_left_capacity_bytes = 2 * 1024 * 1024 * 1024; // 2G

    /*
     * If capacity of disk reach the 'storage_flood_stage_usage_percent' and 'storage_flood_stage_left_capacity_bytes',
     * the following operation will be rejected:
     * 1. load job
     * 2. restore job
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int storage_flood_stage_usage_percent = 95;
    @ConfField(mutable = true, masterOnly = true)
    public static long storage_flood_stage_left_capacity_bytes = 1 * 1024 * 1024 * 1024; // 100MB

    // update interval of tablet stat
    // All frontends will get tablet stat from all backends at each interval
    @ConfField public static int tablet_stat_update_interval_second = 300;  // 5 min

    // May be necessary to modify the following BRPC configurations in high concurrency scenarios. 
    // The number of concurrent requests BRPC can processed
    @ConfField public static int brpc_number_of_concurrent_requests_processed = 4096;

    // BRPC idle wait time (ms)
    @ConfField public static int brpc_idle_wait_max_time = 10000;
    
    /*
     * if set to false, auth check will be disable, in case some goes wrong with the new privilege system. 
     */
    @ConfField public static boolean enable_auth_check = true;
    
    /*
     * Max bytes a broker scanner can process in one broker load job.
     * Commonly, each Backends has one broker scanner.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static long max_bytes_per_broker_scanner = 3 * 1024 * 1024 * 1024L; // 3G
    
    /*
     * Max number of load jobs, include PENDING、ETL、LOADING、QUORUM_FINISHED.
     * If exceed this number, load job is not allowed to be submitted.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static long max_unfinished_load_job = 1000;
    
    /*
     * If set to true, Planner will try to select replica of tablet on same host as this Frontend.
     * This may reduce network transmission in following case:
     * 1. N hosts with N Backends and N Frontends deployed.
     * 2. The data has N replicas.
     * 3. High concurrency queries are sent to all Frontends evenly
     * In this case, all Frontends can only use local replicas to do the query.
     */
    @ConfField(mutable = true)
    public static boolean enable_local_replica_selection = false;
    
    /*
     * The timeout of executing async remote fragment.
     * In normal case, the async remote fragment will be executed in a short time. If system are under high load
     * condition，try to set this timeout longer.
     */
    @ConfField(mutable = true)
    public static long remote_fragment_exec_timeout_ms = 5000; // 5 sec
    
    /*
     * The number of query retries. 
     * A query may retry if we encounter RPC exception and no result has been sent to user.
     * You may reduce this number to void Avalanche disaster.
     */
    @ConfField(mutable = true)
    public static int max_query_retry_time = 3;

    /*
     * The tryLock timeout configuration of catalog lock.
     * Normally it does not need to change, unless you need to test something.
     */
    @ConfField(mutable = true)
    public static long catalog_try_lock_timeout_ms = 5000; // 5 sec
    
    /*
     * if this is set to true
     *    all pending load job will failed when call begin txn api
     *    all prepare load job will failed when call commit txn api
     *    all committed load job will waiting to be published
     */
    @ConfField(mutable = true, masterOnly = true)
    public static boolean disable_load_job = false;
    
    /*
     * Load using hadoop cluster will be deprecated in future.
     * Set to true to disable this kind of load.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static boolean disable_hadoop_load = false;
    
    /*
     * fe will call es api to get es index shard info every es_state_sync_interval_secs
     */
    @ConfField
    public static long es_state_sync_interval_second = 10;
    
    /*
     * the factor of delay time before deciding to repair tablet.
     * if priority is VERY_HIGH, repair it immediately.
     * HIGH, delay tablet_repair_delay_factor_second * 1;
     * NORMAL: delay tablet_repair_delay_factor_second * 2;
     * LOW: delay tablet_repair_delay_factor_second * 3;
     */
    @ConfField(mutable = true, masterOnly = true)
    public static long tablet_repair_delay_factor_second = 60;
    
    /*
     * the default slot number per path in tablet scheduler
     * TODO(cmy): remove this config and dynamically adjust it by clone task statistic
     */
    @ConfField public static int schedule_slot_num_per_path = 2;
    
    /*
     * Deprecated after 0.10
     */
    @ConfField public static boolean use_new_tablet_scheduler = true;

    /*
     * the threshold of cluster balance score, if a backend's load score is 10% lower than average score,
     * this backend will be marked as LOW load, if load score is 10% higher than average score, HIGH load
     * will be marked.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static double balance_load_score_threshold = 0.1; // 10%

    /*
     * if set to true, TabletScheduler will not do balance.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static boolean disable_balance = false;
    
    // This threshold is to avoid piling up too many report task in FE, which may cause OOM exception.
    // In some large Doris cluster, eg: 100 Backends with ten million replicas, a tablet report may cost
    // several seconds after some modification of metadata(drop partition, etc..).
    // And one Backend will report tablets info every 1 min, so unlimited receiving reports is unacceptable.
    // TODO(cmy): we will optimize the processing speed of tablet report in future, but now, just discard
    // the report if queue size exceeding limit.
    // Some online time cost:
    // 1. disk report: 0-1 ms
    // 2. task report: 0-1 ms
    // 3. tablet report 
    //      10000 replicas: 200ms
    @ConfField(mutable = true, masterOnly = true)
    public static int report_queue_size = 100;
    
    /*
     * If set to true, metric collector will be run as a daemon timer to collect metrics at fix interval
     */
    @ConfField public static boolean enable_metric_calculator = true;

    /*
     * the max concurrent task num of a routine load task
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int max_routine_load_task_concurrent_num = 5;

    /*
     * the max concurrent task num per be
     * The cluster max concurrent task num = max_concurrent_task_num_per_be * number of be
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int max_concurrent_task_num_per_be = 10;

    /*
     * The max number of files store in SmallFileMgr 
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int max_small_file_number = 100;

    /*
     * The max size of a single file store in SmallFileMgr 
     */
    @ConfField(mutable = true, masterOnly = true)
    public static int max_small_file_size_bytes = 1024 * 1024; // 1MB

    /*
     * Save small files
     */
    @ConfField public static String small_file_dir = System.getenv("DORIS_HOME") + "/small_files";
    
    /*
     * The following 2 configs can set to true to disable the automatic colocate tables's relocate and balance.
     * if 'disable_colocate_relocate' is set to true, ColocateTableBalancer will not relocate colocate tables when Backend unavailable.
     * if 'disable_colocate_balance' is set to true, ColocateTableBalancer will not balance colocate tables.
     */
    @ConfField(mutable = true, masterOnly = true) public static boolean disable_colocate_relocate = false;
    @ConfField(mutable = true, masterOnly = true) public static boolean disable_colocate_balance = false;

    /*
     * If set to true, the insert stmt with processing error will still return a label to user.
     * And user can use this label to check the load job's status.
     * The default value is false, which means if insert operation encounter errors,
     * exception will be thrown to user client directly without load label.
     */
    @ConfField(mutable = true, masterOnly = true) public static boolean using_old_load_usage_pattern = false;

    /*
     * This will limit the max recursion depth of hash distribution pruner.
     * eg: where a in (5 elements) and b in (4 elements) and c in (3 elements) and d in (2 elements).
     * a/b/c/d are distribution columns, so the recursion depth will be 5 * 4 * 3 * 2 = 120, larger than 100,
     * So that distribution pruner will no work and just return all buckets.
     * 
     * Increase the depth can support distribution pruning for more elements, but may cost more CPU.
     */
    @ConfField(mutable = true, masterOnly = false)
    public static int max_distribution_pruner_recursion_depth = 100;

    /*
     * If the jvm memory used percent(heap or old mem pool) exceed this threshold, checkpoint thread will
     * not work to avoid OOM.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static long metadata_checkopoint_memory_threshold = 60;

    /*
     * If set to true, the checkpoint thread will make the checkpoint regardless of the jvm memory used percent.
     */
    @ConfField(mutable = true, masterOnly = true)
    public static boolean force_do_metadata_checkpoint = false;

}

