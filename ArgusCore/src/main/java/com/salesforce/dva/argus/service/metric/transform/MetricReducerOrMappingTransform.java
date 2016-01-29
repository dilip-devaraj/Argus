/*
 * Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
	 
package com.salesforce.dva.argus.service.metric.transform;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.system.SystemAssert;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For some function, it either does a mapping transform or reduce transform which depends on the constant input This class provides a general
 * transform for either mapping if no constant input, or reduing with a constant input.
 *
 * <p>So far, Such functions include: List<Metric> DIFF(List <Metric> metrics, Double constant); List<Metric> DIVIDE(List<Metric> metrics, Double
 * constant); List<Metric> SCALE(List<Metric> metrics, Double constant); List<Metric> SUM(List<Metric> metrics, Double constant);</p>
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class MetricReducerOrMappingTransform implements Transform {

    //~ Instance fields ******************************************************************************************************************************

    protected final ValueReducerOrMapping valueReducerOrMapping;
    protected final String defaultScope;
    protected final String defaultMetricName;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new ReduceTransform object.
     *
     * @param  valueReducerOrMapping  The valueMapping.
     */
    protected MetricReducerOrMappingTransform(ValueReducerOrMapping valueReducerOrMapping) {
        this.valueReducerOrMapping = valueReducerOrMapping;
        this.defaultScope = valueReducerOrMapping.name();
        this.defaultMetricName = TransformFactory.DEFAULT_METRIC_NAME;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getResultScopeName() {
        return defaultScope;
    }

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        return Arrays.asList(reduce(metrics));
    }

    /**
     * If constants is not null, apply mapping transform to metrics list. Otherwise, apply reduce transform to metrics list
     *
     * @param   metrics    list of metrics
     * @param   constants  constants input
     *
     * @return  A list of metrics after mapping.
     */
    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        if (constants == null || constants.isEmpty()) {
            return transform(metrics);
        }
        return mapping(metrics, constants);
    }

    /**
     * Mapping a list of metric, only massage its datapoints.
     *
     * @param   metrics    The list of metrics to be mapped. constants The list of constants used for mapping
     * @param   constants  constants input
     *
     * @return  A list of metrics after mapping.
     */
    protected List<Metric> mapping(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform empty metric/metrics");
        if (metrics.isEmpty()) {
            return metrics;
        }

        List<Metric> newMetricsList = new ArrayList<Metric>();

        for (Metric metric : metrics) {
            metric.setDatapoints(this.valueReducerOrMapping.mapping(metric.getDatapoints(), constants));
            newMetricsList.add(metric);
        }
        return newMetricsList;
    }

    /**
     * Reduce transform for the list of metrics.
     *
     * @param   metrics  The list of metrics to reduce.
     *
     * @return  The reduced metric.
     */
    protected Metric reduce(List<Metric> metrics) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform empty metric/metrics");

        /*
         * if (metrics.isEmpty()) { return new Metric(defaultScope, defaultMetricName); }
         */
        MetricDistiller distiller = new MetricDistiller();

        distiller.distill(metrics);

        Map<Long, List<String>> collated = collate(metrics);
        Map<Long, String> minDatapoints = reduce(collated, metrics);
        String newMetricName = distiller.getMetric() == null ? defaultMetricName : distiller.getMetric();
        Metric newMetric = new Metric(defaultScope, newMetricName);

        newMetric.setDisplayName(distiller.getDisplayName());
        newMetric.setUnits(distiller.getUnits());
        newMetric.setTags(distiller.getTags());
        newMetric.setDatapoints(minDatapoints);
        return newMetric;
    }

    private Map<Long, List<String>> collate(List<Metric> metrics) {
        Map<Long, List<String>> collated = new HashMap<Long, List<String>>();

        for (Metric metric : metrics) {
            for (Map.Entry<Long, String> point : metric.getDatapoints().entrySet()) {
                if (!collated.containsKey(point.getKey())) {
                    collated.put(point.getKey(), new ArrayList<String>());
                }
                collated.get(point.getKey()).add(point.getValue());
            }
        }
        return collated;
    }

    private Map<Long, String> reduce(Map<Long, List<String>> collated, List<Metric> metrics) {
        Map<Long, String> reducedDatapoints = new HashMap<>();

        for (Map.Entry<Long, List<String>> entry : collated.entrySet()) {
            if (entry.getValue().size() < metrics.size()) {
                continue;
            }
            reducedDatapoints.put(entry.getKey(), this.valueReducerOrMapping.reduce(entry.getValue()));
        }
        return reducedDatapoints;
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("ReducerOrMapping doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */