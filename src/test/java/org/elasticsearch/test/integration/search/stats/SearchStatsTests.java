/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.test.integration.search.stats;

import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.elasticsearch.action.admin.cluster.node.stats.NodesStatsResponse;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.test.integration.AbstractSharedClusterTest;
import org.testng.annotations.Test;

/**
 */
public class SearchStatsTests extends AbstractSharedClusterTest {
    
    @Override
    public Settings getSettings() {
        return randomSettingsBuilder()
                .put("index.number_of_replicas", 0)
                .build();
    }

    @Test
    public void testSimpleStats() throws Exception {
        createIndex("test1");
        for (int i = 0; i < 500; i++) {
            client().prepareIndex("test1", "type", Integer.toString(i)).setSource("field", "value").execute().actionGet();
            if (i == 10) {
                refresh();
            }
        }
        createIndex("test2");
        for (int i = 0; i < 500; i++) {
            client().prepareIndex("test2", "type", Integer.toString(i)).setSource("field", "value").execute().actionGet();
            if (i == 10) {
                refresh();
            }
        }
        cluster().ensureAtMostNumNodes(numAssignedShards("test1", "test2"));
        for (int i = 0; i < 200; i++) {
            client().prepareSearch().setQuery(QueryBuilders.termQuery("field", "value")).setStats("group1", "group2").execute().actionGet();
        }

        IndicesStatsResponse indicesStats = client().admin().indices().prepareStats().execute().actionGet();
        assertThat(indicesStats.getTotal().getSearch().getTotal().getQueryCount(), greaterThan(0l));
        assertThat(indicesStats.getTotal().getSearch().getTotal().getQueryTimeInMillis(), greaterThan(0l));
        assertThat(indicesStats.getTotal().getSearch().getTotal().getFetchCount(), greaterThan(0l));
        assertThat(indicesStats.getTotal().getSearch().getTotal().getFetchTimeInMillis(), greaterThan(0l));
        assertThat(indicesStats.getTotal().getSearch().getGroupStats(), nullValue());

        indicesStats = client().admin().indices().prepareStats().setGroups("group1").execute().actionGet();
        assertThat(indicesStats.getTotal().getSearch().getGroupStats(), notNullValue());
        assertThat(indicesStats.getTotal().getSearch().getGroupStats().get("group1").getQueryCount(), greaterThan(0l));
        assertThat(indicesStats.getTotal().getSearch().getGroupStats().get("group1").getQueryTimeInMillis(), greaterThan(0l));
        assertThat(indicesStats.getTotal().getSearch().getGroupStats().get("group1").getFetchCount(), greaterThan(0l));
        assertThat(indicesStats.getTotal().getSearch().getGroupStats().get("group1").getFetchTimeInMillis(), greaterThan(0l));

        NodesStatsResponse nodeStats = client().admin().cluster().prepareNodesStats().execute().actionGet();
        assertThat(nodeStats.getNodes()[0].getIndices().getSearch().getTotal().getQueryCount(), greaterThan(0l));
        assertThat(nodeStats.getNodes()[0].getIndices().getSearch().getTotal().getQueryTimeInMillis(), greaterThan(0l));
    }

    @Test
    public void testOpenContexts() {
        createIndex("test1");
        for (int i = 0; i < 50; i++) {
            client().prepareIndex("test1", "type", Integer.toString(i)).setSource("field", "value").execute().actionGet();
        }
        IndicesStatsResponse indicesStats = client().admin().indices().prepareStats().execute().actionGet();
        assertThat(indicesStats.getTotal().getSearch().getOpenContexts(), equalTo(0l));

        SearchResponse searchResponse = client().prepareSearch()
                .setSearchType(SearchType.SCAN)
                .setQuery(matchAllQuery())
                .setSize(5)
                .setScroll(TimeValue.timeValueMinutes(2))
                .execute().actionGet();

        indicesStats = client().admin().indices().prepareStats().execute().actionGet();
        assertThat(indicesStats.getTotal().getSearch().getOpenContexts(), equalTo((long)numAssignedShards("test1")));

        // scroll, but with no timeout (so no context)
        searchResponse = client().prepareSearchScroll(searchResponse.getScrollId()).execute().actionGet();

        indicesStats = client().admin().indices().prepareStats().execute().actionGet();
        assertThat(indicesStats.getTotal().getSearch().getOpenContexts(), equalTo(0l));
    }
}
