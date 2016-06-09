/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.eagle.security.hbase;

import backtype.storm.generated.StormTopology;
import backtype.storm.topology.BoltDeclarer;
import backtype.storm.topology.IRichSpout;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.tuple.Fields;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.eagle.security.topo.NewKafkaSourcedSpoutProvider;
import org.apache.eagle.security.topo.TopologySubmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import storm.kafka.*;

public class HbaseAuditLogMonitoringMain {
    private static Logger LOG = LoggerFactory.getLogger(HbaseAuditLogMonitoringMain.class);
    public final static String SPOUT_TASK_NUM = "topology.numOfSpoutTasks";
    public final static String PARSER_TASK_NUM = "topology.numOfParserTasks";
    public final static String JOIN_TASK_NUM = "topology.numOfJoinTasks";

    public static void main(String[] args) throws Exception{
        System.setProperty("config.resource", "/application.conf");
        Config config = ConfigFactory.load();
        NewKafkaSourcedSpoutProvider provider = new NewKafkaSourcedSpoutProvider();
        IRichSpout spout = provider.getSpout(config);

        HBaseAuditLogParserBolt bolt = new HBaseAuditLogParserBolt();
        TopologyBuilder builder = new TopologyBuilder();

        int numOfSpoutTasks = config.getInt(SPOUT_TASK_NUM);
        int numOfParserTasks = config.getInt(PARSER_TASK_NUM);
        int numOfJoinTasks = config.getInt(JOIN_TASK_NUM);

        builder.setSpout("ingest", spout, numOfSpoutTasks);
        BoltDeclarer boltDeclarer = builder.setBolt("parserBolt", bolt, numOfParserTasks);
        boltDeclarer.fieldsGrouping("ingest", new Fields(StringScheme.STRING_SCHEME_KEY));

        HbaseResourceSensitivityDataJoinBolt joinBolt = new HbaseResourceSensitivityDataJoinBolt(config);
        BoltDeclarer joinBoltDeclarer = builder.setBolt("joinBolt", joinBolt, numOfJoinTasks);
        joinBoltDeclarer.fieldsGrouping("parserBolt", new Fields("f1"));

        StormTopology topology = builder.createTopology();

        TopologySubmitter.submit(topology, config);
    }
}