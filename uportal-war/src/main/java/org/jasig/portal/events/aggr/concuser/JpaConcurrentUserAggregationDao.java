/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.portal.events.aggr.concuser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import org.jasig.portal.events.aggr.AggregationInterval;
import org.jasig.portal.events.aggr.BaseAggregationImpl_;
import org.jasig.portal.events.aggr.DateDimension;
import org.jasig.portal.events.aggr.JpaBaseAggregationDao;
import org.jasig.portal.events.aggr.TimeDimension;
import org.jasig.portal.events.aggr.dao.jpa.DateDimensionImpl;
import org.jasig.portal.events.aggr.dao.jpa.DateDimensionImpl_;
import org.jasig.portal.events.aggr.dao.jpa.TimeDimensionImpl;
import org.jasig.portal.events.aggr.dao.jpa.TimeDimensionImpl_;
import org.jasig.portal.events.aggr.groups.AggregatedGroupMapping;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.springframework.stereotype.Repository;

/**
 * DAO for Concurrent User Aggregations
 * 
 * @author Eric Dalquist
 */
@Repository
public class JpaConcurrentUserAggregationDao extends
        JpaBaseAggregationDao<ConcurrentUserAggregationImpl, ConcurrentUserAggregationKey> implements
        ConcurrentUserAggregationPrivateDao {

    public JpaConcurrentUserAggregationDao() {
        super(ConcurrentUserAggregationImpl.class);
    }

    protected CriteriaQuery<ConcurrentUserAggregationImpl> findAllConcurrentUserAggregationsByDateRangeQuery;

    @Override
    protected void createCriteriaQueries() {
        this.findAllConcurrentUserAggregationsByDateRangeQuery = this.createCriteriaQuery(new Function<CriteriaBuilder, CriteriaQuery<ConcurrentUserAggregationImpl>>() {
            @Override
            public CriteriaQuery<ConcurrentUserAggregationImpl> apply(CriteriaBuilder cb) {
                final CriteriaQuery<ConcurrentUserAggregationImpl> criteriaQuery = cb.createQuery(ConcurrentUserAggregationImpl.class);

                final Root<ConcurrentUserAggregationImpl> ba = criteriaQuery.from(ConcurrentUserAggregationImpl.class);
                final Join<ConcurrentUserAggregationImpl, DateDimensionImpl> dd = ba.join(BaseAggregationImpl_.dateDimension, JoinType.LEFT);
                final Join<ConcurrentUserAggregationImpl, TimeDimensionImpl> td = ba.join(BaseAggregationImpl_.timeDimension, JoinType.LEFT);


                final List<Predicate> keyPredicates = new ArrayList<Predicate>();
                keyPredicates.add(cb.and( //Restrict results by outer date range
                        cb.greaterThanOrEqualTo(dd.get(DateDimensionImpl_.date), startDate),
                        cb.lessThan(dd.get(DateDimensionImpl_.date), endPlusOneDate)
                ));
                keyPredicates.add(cb.or( //Restrict start of range by time as well
                        cb.greaterThan(dd.get(DateDimensionImpl_.date), startDate),
                        cb.greaterThanOrEqualTo(td.get(TimeDimensionImpl_.time), startTime)
                ));
                keyPredicates.add(cb.or( //Restrict end of range by time as well
                        cb.lessThan(dd.get(DateDimensionImpl_.date), endDate),
                        cb.lessThan(td.get(TimeDimensionImpl_.time), endTime)
                ));
                keyPredicates.add(cb.equal(ba.get(BaseAggregationImpl_.interval), intervalParameter));
                keyPredicates.add(ba.get(BaseAggregationImpl_.aggregatedGroup).in(aggregatedGroupsParameter));

                criteriaQuery.select(ba);
                criteriaQuery.where(keyPredicates.toArray(new Predicate[keyPredicates.size()]));
                criteriaQuery.orderBy(cb.desc(dd.get(DateDimensionImpl_.date)), cb.desc(td.get(TimeDimensionImpl_.time)));

                return criteriaQuery;
            }
        });
    }

    @Override
    protected void addFetches(Root<ConcurrentUserAggregationImpl> root) {
        root.fetch(ConcurrentUserAggregationImpl_.uniqueStrings, JoinType.LEFT);        
    }
    
    @Override
    protected void addUnclosedPredicate(CriteriaBuilder cb, Root<ConcurrentUserAggregationImpl> root,
            List<Predicate> keyPredicates) {
        keyPredicates.add(cb.isNotNull(root.get(ConcurrentUserAggregationImpl_.uniqueStrings)));
    }

    @Override
    protected ConcurrentUserAggregationImpl createAggregationInstance(ConcurrentUserAggregationKey key) {
        final TimeDimension timeDimension = key.getTimeDimension();
        final DateDimension dateDimension = key.getDateDimension();
        final AggregationInterval interval = key.getInterval();
        final AggregatedGroupMapping aggregatedGroup = key.getAggregatedGroup();
        return new ConcurrentUserAggregationImpl(timeDimension, dateDimension, interval, aggregatedGroup);
    }

    @Override
    protected ConcurrentUserAggregationKey getAggregationKey(ConcurrentUserAggregationImpl instance) {
        return instance.getAggregationKey();
    }

    @Override
    public final List<ConcurrentUserAggregationImpl> getAggregations(DateTime start, DateTime end, AggregationInterval interval,
                                                                                  AggregatedGroupMapping aggregatedGroupMapping, AggregatedGroupMapping... aggregatedGroupMappings) {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Start must be before End: " + start + " - " + end);
        }
        final LocalDate startDate = start.toLocalDate();
        final LocalDate endDate = end.toLocalDate();

        final TypedQuery<ConcurrentUserAggregationImpl> query = this.createQuery(this.findAllConcurrentUserAggregationsByDateRangeQuery);

        query.setParameter(this.startDate, startDate);
        query.setParameter(this.startTime, start.toLocalTime());

        query.setParameter(this.endDate, endDate);
        query.setParameter(this.endTime, end.toLocalTime());
        query.setParameter(this.endPlusOneDate, endDate.plusDays(1));

        query.setParameter(this.intervalParameter, interval);

        final Set<AggregatedGroupMapping> groups = ImmutableSet.<AggregatedGroupMapping>builder().add(aggregatedGroupMapping).add(aggregatedGroupMappings).build();
        query.setParameter(this.aggregatedGroupsParameter, groups);

        return query.getResultList();
    }
}
