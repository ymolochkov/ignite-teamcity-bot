/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.tcbot.engine.buildtime;

import org.apache.ignite.IgniteCache;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.tcbot.common.util.TimeUtil;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.ui.BuildTimeRecordUi;
import org.apache.ignite.tcbot.engine.ui.BuildTimeResultUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcignited.buildref.BuildRefDao;
import org.apache.ignite.tcignited.buildtime.BuildTimeRecord;
import org.apache.ignite.tcignited.buildtime.BuildTimeResult;
import org.apache.ignite.tcignited.creds.ICredentialsProv;
import org.apache.ignite.tcignited.history.HistoryCollector;
import org.apache.ignite.tcignited.history.RunHistCompactedDao;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.result.stat.Statistics;

import javax.cache.Cache;
import javax.inject.Inject;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class BuildTimeService {

    @Inject ITeamcityIgnitedProvider tcProv;

    /** Config. */
    @Inject ITcBotConfig cfg;

    @Inject FatBuildDao fatBuildDao;

    @Inject BuildRefDao buildRefDao;

    @Inject RunHistCompactedDao runHistCompactedDao;

    @Inject IStringCompactor compactor;

    @Inject HistoryCollector historyCollector;

    public BuildTimeResultUi analytics(ICredentialsProv prov) {
        String serverCode = cfg.primaryServerCode();

        ITeamcityIgnited server = tcProv.server(serverCode, prov);

        // fatBuildDao.loadBuildTimeResult();

        Collection<String> allServers = cfg.getServerIds();

        int days = 1;
        List<Long> idsToCheck = forEachBuildRef(days, allServers);

        BuildTimeResult res = fatBuildDao.loadBuildTimeResult(days, idsToCheck);

        Set<Integer> availableServers = allServers.stream()
                .filter(prov::hasAccess)
                .map(ITeamcityIgnited::serverIdToInt)
                .collect(Collectors.toSet());

        BuildTimeResultUi resultUi = new BuildTimeResultUi();

        long minDuration = Duration.ofHours(1).toMillis();
        List<Map.Entry<Long, BuildTimeRecord>> entries = res.topBuildTypes(availableServers, minDuration);
        entries.forEach(e->{
            BuildTimeRecordUi buildTimeRecordUi = new BuildTimeRecordUi();
            Long key = e.getKey();
            int btId = BuildTimeResult.cacheKeyToBuildType(key);
            buildTimeRecordUi.buildType = compactor.getStringFromId(btId);

            buildTimeRecordUi.averageDuration = TimeUtil.millisToDurationPrintable(e.getValue().avgDuration());
            resultUi.byBuildType.add(buildTimeRecordUi);
        });



        return resultUi;
    }

    public List<Long> forEachBuildRef(int days, Collection<String> allServers) {
        IgniteCache<Long, BinaryObject> cacheBin = buildRefDao.buildRefsCache().withKeepBinary();

        Set<Integer> availableServers = allServers.stream()
                .map(ITeamcityIgnited::serverIdToInt)
                .collect(Collectors.toSet());

        Map<Integer, Integer> preBorder = new HashMap<>();

        availableServers.forEach(srvId -> {
            Integer borderForAgeForBuildId = runHistCompactedDao.getBorderForAgeForBuildId(srvId, days);
            if (borderForAgeForBuildId != null)
                preBorder.put(srvId, borderForAgeForBuildId);
        });

        int stateRunning = compactor.getStringId(BuildRef.STATE_RUNNING);
        final int stateQueued = compactor.getStringId(BuildRef.STATE_QUEUED);
        Integer buildDurationId = compactor.getStringIdIfPresent(Statistics.BUILD_DURATION);

        long minTs = System.currentTimeMillis() - Duration.ofDays(days).toMillis();
        QueryCursor<Cache.Entry<Long, BinaryObject>> query = cacheBin.query(
            new ScanQuery<Long, BinaryObject>()
                .setFilter((key, v) -> {
                    int srvId = BuildRefDao.cacheKeyToSrvId(key);
                    Integer buildIdBorder = preBorder.get(srvId);
                    if (buildIdBorder != null) {
                        int id = v.field("id");
                        if (id < buildIdBorder)
                            return false;// pre-filtered build out of scope
                    }
                    int state = v.field("state");

                    return stateQueued != state;
                }));

        int cnt = 0;
        List<Long> idsToCheck = new ArrayList<>();

        try (QueryCursor<Cache.Entry<Long, BinaryObject>> cursor = query) {
            for (Cache.Entry<Long, BinaryObject> next : cursor) {
                Long key = next.getKey();
                int srvId = BuildRefDao.cacheKeyToSrvId(key);

                int buildId = BuildRefDao.cacheKeyToBuildId(key);

                Integer borderBuildId = runHistCompactedDao.getBorderForAgeForBuildId(srvId, days);

                boolean passesDate = borderBuildId == null || buildId >= borderBuildId;

                if (!passesDate)
                    continue;

                Long startTs = historyCollector.getBuildStartTime(srvId, buildId);
                if (startTs == null || startTs < minTs)
                    continue; //time not saved in the DB, skip

                BinaryObject buildBinary = next.getValue();
                long runningTime = 0l;// getBuildRunningTime(stateRunning, buildDurationId, buildBinary);

                System.err.println("Found build at srv [" + srvId + "]: [" + buildId + "] to analyze, ts="+ startTs);

                cnt++;

                idsToCheck.add(key);
            }
        }

        System.err.println("Total builds to load " + cnt);

        // serversCompute.call(new BuildTimeIgniteCallable(cacheBin, stateRunning, buildDurationId));

        return idsToCheck;
    }
}
