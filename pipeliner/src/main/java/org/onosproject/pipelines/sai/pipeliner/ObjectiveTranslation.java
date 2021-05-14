/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.pipelines.sai.pipeliner;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.onosproject.net.flow.FlowId;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flowobjective.ObjectiveError;
import org.onosproject.net.group.GroupDescription;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/**
 * Result of a pipeliner translation from an objective to flows.
 */
final class ObjectiveTranslation {

    private final ImmutableList<ImmutableSet<FlowRule>> flowRulesStages;
    private final ImmutableMap<Integer, GroupDescription> groups;
    private final ObjectiveError error;

    private ObjectiveTranslation(ImmutableList<ImmutableSet<FlowRule>> flowRulesStages,
                                 Map<Integer, GroupDescription> groups,
                                 ObjectiveError error) {
        this.flowRulesStages = flowRulesStages;
        this.groups = ImmutableMap.copyOf(groups);
        this.error = error;
    }

    /**
     * Returns all flow rules of all translation stages.
     *
     * @return flow rules
     */
    Collection<FlowRule> flowRules() {
        ImmutableList.Builder<FlowRule> flowRuleListBuilder = ImmutableList.builder();
        flowRulesStages.forEach(flowRuleListBuilder::addAll);
        return flowRuleListBuilder.build();
    }

    /**
     * Returns all the translation stages with the corresponding flow rules.
     *
     * @return translation stages
     */
    ImmutableList<ImmutableSet<FlowRule>> stages() {
        return flowRulesStages;
    }

    /**
     * Returns groups of this translation.
     *
     * @return groups
     */
    Collection<GroupDescription> groups() {
        return groups.values();
    }


    /**
     * Returns the error of this translation, if any.
     *
     * @return optional error
     */
    Optional<ObjectiveError> error() {
        return Optional.ofNullable(error);
    }

    /**
     * Creates a new builder.
     *
     * @return the builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new translation that signals the given error.
     *
     * @param error objective error
     * @return new objective translation
     */
    static ObjectiveTranslation ofError(ObjectiveError error) {
        checkNotNull(error);
        return new ObjectiveTranslation(ImmutableList.of(), Collections.emptyMap(), error);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("flowRules", flowRulesStages)
                .add("error", error)
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(flowRulesStages, error);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final ObjectiveTranslation other = (ObjectiveTranslation) obj;
        if (this.flowRulesStages.size() != other.flowRulesStages.size()) {
            return false;
        }
        for (int i = 0; i < this.flowRulesStages.size(); i++) {
            if (!flowRulesExactMatch(this.flowRulesStages.get(i), other.flowRulesStages.get(i))) {
                return false;
            }
        }
        return Objects.equals(this.error, other.error);
    }

    private static boolean flowRulesExactMatch(Set<FlowRule> thisFlowRules, Set<FlowRule> otherFlowRules) {
        if (otherFlowRules == null || otherFlowRules.size() != thisFlowRules.size()) {
            return false;
        }
        return otherFlowRules.containsAll(thisFlowRules);
    }

    /**
     * Builder for ObjectiveTranslation. This implementation checks that flow rule ID
     * are unique in each stage.
     * that flow ID are unique.
     */
    static final class Builder {

        private final ImmutableList.Builder<ImmutableSet<FlowRule>> flowRulesBuilder = ImmutableList.builder();
        private Map<FlowId, FlowRule> currentStage = Maps.newHashMap();
        private final Map<Integer, GroupDescription> groups = Maps.newHashMap();

        // Hide default constructor
        private Builder() {
        }

        /**
         * Adds a flow rule to this translation stage. Every stage has unique flows ID.
         *
         * @param flowRule flow rule
         * @return this
         * @throws SaiPipelinerException if a FlowRule with same FlowId
         *                                  already exists in this translation
         */
        Builder addFlowRule(FlowRule flowRule)
                throws SaiPipelinerException {
            checkNotNull(flowRule);
            if (currentStage.containsKey(flowRule.id())) {
                final FlowRule existingFlowRule = currentStage.get(flowRule.id());
                if (!existingFlowRule.exactMatch(flowRule)) {
                    throw new SaiPipelinerException(format(
                            "Another FlowRule with same ID has already been " +
                                    "added to this translation: existing=%s, new=%s",
                            existingFlowRule, flowRule));
                }
            }
            currentStage.put(flowRule.id(), flowRule);
            return this;
        }

        /**
         * Adds group to this translation.
         *
         * @param group group
         * @return this
         * @throws SaiPipelinerException if a FlowRule with same GroupId already
         *                               exists in this translation
         */
        Builder addGroup(GroupDescription group)
                throws SaiPipelinerException {
            checkNotNull(group);
            if (groups.containsKey(group.givenGroupId())) {
                final GroupDescription existingGroup = groups.get(group.givenGroupId());
                if (!existingGroup.equals(group)) {
                    throw new SaiPipelinerException(format(
                            "Another Group with same ID has already been " +
                                    "added to this translation: existing=%s, new=%s",
                            existingGroup, group));
                }
            }
            groups.put(group.givenGroupId(), group);
            return this;
        }

        Builder addFlowRules(Collection<FlowRule> flowRules) throws SaiPipelinerException {
            for (var flowRule : flowRules) {
                this.addFlowRule(flowRule);
            }
            return this;
        }

        private void closeStage() {
            ImmutableSet<FlowRule> stage = ImmutableSet.copyOf(currentStage.values());
            if (!stage.isEmpty()) {
                flowRulesBuilder.add(stage);
            }
        }

        Builder newStage() {
            closeStage();
            currentStage = Maps.newHashMap();
            return this;
        }

        /**
         * Creates ane translation.
         *
         * @return translation instance
         */
        ObjectiveTranslation build() {
            closeStage();
            return new ObjectiveTranslation(flowRulesBuilder.build(), groups, null);
        }
    }
}