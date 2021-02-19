/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.pipelines.sai;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.onosproject.net.flow.FlowId;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flowobjective.ObjectiveError;

import java.util.Collection;
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
    private final ObjectiveError error;

    private ObjectiveTranslation(ImmutableList<ImmutableSet<FlowRule>> flowRulesStages,
                                 ObjectiveError error) {
        this.flowRulesStages = flowRulesStages;
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

    ImmutableList<ImmutableSet<FlowRule>> stages() {
        return flowRulesStages;
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
        return new ObjectiveTranslation(ImmutableList.of(),  error);
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
            return new ObjectiveTranslation(flowRulesBuilder.build(), null);
        }
    }
}