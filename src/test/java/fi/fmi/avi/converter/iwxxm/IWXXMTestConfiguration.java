package fi.fmi.avi.converter.iwxxm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.w3c.dom.Document;

import fi.fmi.avi.converter.AviMessageConverter;
import fi.fmi.avi.converter.AviMessageSpecificConverter;
import fi.fmi.avi.converter.iwxxm.conf.IWXXMConverter;
import fi.fmi.avi.model.sigmet.SIGMET;
import fi.fmi.avi.model.taf.TAF;

@Configuration
@Import(IWXXMConverter.class)
public class IWXXMTestConfiguration {
    
    @Autowired
    private AviMessageSpecificConverter<TAF, Document> tafIWXXMDOMSerializer;
    
    @Autowired
    private AviMessageSpecificConverter<TAF, String> tafIWXXMStringSerializer;

    @Autowired
    private AviMessageSpecificConverter<Document, TAF> tafIWXXMDOMParser;

    @Autowired
    private AviMessageSpecificConverter<String, TAF> tafIWXXMStringParser;

    @Autowired
    private AviMessageSpecificConverter<SIGMET, Document> sigmetIWXXMDOMSerializer;

    @Autowired
    private AviMessageSpecificConverter<SIGMET, String> sigmetIWXXMStringSerializer;

    @Bean
    public AviMessageConverter aviMessageConverter() {
        AviMessageConverter p = new AviMessageConverter();
        p.setMessageSpecificConverter(IWXXMConverter.TAF_POJO_TO_IWXXM21_DOM,tafIWXXMDOMSerializer);
        p.setMessageSpecificConverter(IWXXMConverter.TAF_POJO_TO_IWXXM21_STRING,tafIWXXMStringSerializer);
        p.setMessageSpecificConverter(IWXXMConverter.IWXXM21_STRING_TO_TAF_POJO, tafIWXXMStringParser);
        p.setMessageSpecificConverter(IWXXMConverter.IWXXM21_DOM_TO_TAF_POJO, tafIWXXMDOMParser);
        p.setMessageSpecificConverter(IWXXMConverter.SIGMET_POJO_TO_IWXXM21_DOM,sigmetIWXXMDOMSerializer);
        p.setMessageSpecificConverter(IWXXMConverter.SIGMET_POJO_TO_IWXXM21_STRING,sigmetIWXXMStringSerializer);
        return p;
    }

}
