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

package org.apache.ignite.ci.jira.pure;

import java.io.IOException;
import org.apache.ignite.ci.jira.Tickets;
import org.jetbrains.annotations.NotNull;

/**
 * Reperesents methods to provide interaction with Jira servers.
 */
public interface IJiraIntegration {

    /** @return JIRA ticket prefix. */
    @NotNull public String ticketPrefix();

    /**
     *
     */
    @NotNull public default String projectName() {
        return ticketPrefix().replaceAll("-", "");
    }

    /**
     * @return Internal identified service ID.
     */
    public void init(String srvId);

    /**
     * @return Internal identified service ID.
     */
    public String getServiceId();

    /**
     * @param ticket JIRA ticket full name. E.g 'IGNITE-5555'.
     * @param comment Comment to be placed in the ticket conversation.
     * @return {@code True} if ticket was succesfully commented. Otherwise - {@code false}.
     * @throws IOException If failed to comment JIRA ticket.
     * @throws IllegalStateException If can't find URL to the JIRA.
     */
    public String postJiraComment(String ticket, String comment) throws IOException;

    /**
     * Produce wrapper for collection of Jira tickets for given server.
     *
     * @param srvId Server id.
     * @param url Tickets loading URL and parameters.
     * @return Jira tickets.
     */
    public Tickets getTicketsPage(@Deprecated String srvId, String url);

    /** */
    public String jiraUrl();

    /**
     * @param ticketFullName Ticket full name (e.g IGNITE-8331)
     * @return URL which is used as link to Jira ticket with specified name.
     */
    public String generateTicketUrl(String ticketFullName);

    /**
     * @param ticketFullName Ticket full name (e.g IGNITE-8331)
     * @return URL which is used as link to Jira comment with specified id.
     */
    public String generateCommentUrl(String ticketFullName, int commentId);

    String getJiraApiUrl();

    boolean isJiraTokenAvailable();
}