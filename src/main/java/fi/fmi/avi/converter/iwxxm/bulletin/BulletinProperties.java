package fi.fmi.avi.converter.iwxxm.bulletin;

import fi.fmi.avi.converter.iwxxm.AbstractPropertyContainer;
import fi.fmi.avi.model.BulletinHeading;
import fi.fmi.avi.model.GenericAviationWeatherMessage;
import wmo.collect2014.MeteorologicalBulletinType;

public class BulletinProperties extends AbstractPropertyContainer<MeteorologicalBulletinType> {

    public enum Name {
        HEADING(BulletinHeading.class),
        MESSAGE(GenericAviationWeatherMessage.class);

        private Class<?> acceptedType;

        Name(final Class<?> type) {
            this.acceptedType = type;
        }

        public Class<?> getAcceptedType() {
            return this.acceptedType;
        }
    }

    public BulletinProperties(final MeteorologicalBulletinType bulletin) {
        super(bulletin);

    }

    @Override
    protected Class<?> getAcceptedType(final Object key) {
        if (Name.class.isAssignableFrom(key.getClass())) {
            return ((Name) key).getAcceptedType();
        } else {
            throw new IllegalArgumentException("Key must be of type " + Name.class.getCanonicalName());
        }
    }
}
