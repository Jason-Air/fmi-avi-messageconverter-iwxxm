package fi.fmi.avi.converter.iwxxm.bulletin;

import java.time.Month;

import fi.fmi.avi.converter.iwxxm.AbstractPropertyContainer;
import fi.fmi.avi.model.BulletinHeading;
import fi.fmi.avi.model.GenericAviationWeatherMessage;

public class BulletinProperties extends AbstractPropertyContainer {

    public enum Name implements PropertyName {
        HEADING(BulletinHeading.class),
        TIMESTAMP_YEAR(Integer.class),
        TIMESTAMP_MONTH(Month.class),
        TIMESTAMP_DAY(Integer.class),
        TIMESTAMP_HOUR(Integer.class),
        TIMESTAMP_MINUTE(Integer.class),
        TIMESTAMP_SECOND(Integer.class),
        MESSAGE(GenericAviationWeatherMessage.class);

        private final Class<?> acceptedType;

        Name(final Class<?> type) {
            this.acceptedType = type;
        }

        @Override
        public Class<?> getAcceptedType() {
            return this.acceptedType;
        }
    }

    public BulletinProperties() {
    }

}
