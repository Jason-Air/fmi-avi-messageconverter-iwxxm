package fi.fmi.avi.converter.iwxxm;

import fi.fmi.avi.model.Aerodrome;
import fi.fmi.avi.model.GeoPosition;
import fi.fmi.avi.model.PartialOrCompleteTime;
import fi.fmi.avi.model.PartialOrCompleteTimeInstant;
import fi.fmi.avi.model.PartialOrCompleteTimePeriod;
import wmo.metce2013.ProcessType;

/**
 * Container class for properties parsed from an IWXXM OMObservationType.
 */
public class OMObservationProperties extends AbstractPropertyContainer {

    public enum Name implements PropertyName {
        TYPE(String.class),//
        PHENOMENON_TIME(PartialOrCompleteTime.class),//
        RESULT_TIME(PartialOrCompleteTimeInstant.class),//
        VALID_TIME(PartialOrCompleteTimePeriod.class),//
        PROCEDURE(ProcessType.class),//
        OBSERVED_PROPERTY(String.class),//
        AERODROME(Aerodrome.class),//
        SAMPLING_POINT(GeoPosition.class),//
        RESULT(Object.class);

        private final Class<?> acceptedType;

        Name(final Class<?> type) {
            this.acceptedType = type;
        }

        @Override
        public Class<?> getAcceptedType() {
            return this.acceptedType;
        }
    }

    public OMObservationProperties() {
    }

}
