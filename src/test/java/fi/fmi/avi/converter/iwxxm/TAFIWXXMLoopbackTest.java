package fi.fmi.avi.converter.iwxxm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import fi.fmi.avi.converter.AviMessageConverter;
import fi.fmi.avi.converter.ConversionHints;
import fi.fmi.avi.converter.ConversionResult;
import fi.fmi.avi.converter.iwxxm.conf.IWXXMConverter;
import fi.fmi.avi.model.taf.TAF;


/**
 * Created by rinne on 19/07/17.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = IWXXMTestConfiguration.class, loader = AnnotationConfigContextLoader.class)
public class TAFIWXXMLoopbackTest {

    @Autowired
    private AviMessageConverter converter;

    private Document readDocument(final String name) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(TAFIWXXMParserTest.class.getResourceAsStream(name));
    }

    @Test
    public void testTAFParsingAndSerialization() throws Exception {
        assertTrue(converter.isSpecificationSupported(IWXXMConverter.IWXXM21_DOM_TO_TAF_POJO));
        assertTrue(converter.isSpecificationSupported(IWXXMConverter.TAF_POJO_TO_IWXXM21_DOM));

        Document toValidate = readDocument("taf-A5-1.xml");
        ConversionResult<TAF> result = converter.convertMessage(toValidate, IWXXMConverter.IWXXM21_DOM_TO_TAF_POJO, ConversionHints.EMPTY);
        assertTrue(ConversionResult.Status.SUCCESS == result.getStatus());
        assertTrue( "No issues should have been found", result.getConversionIssues().isEmpty());
        assertTrue(result.getConvertedMessage().isPresent());

        ConversionResult<Document> result2 = converter.convertMessage(result.getConvertedMessage().get(), IWXXMConverter.TAF_POJO_TO_IWXXM21_DOM);
        assertTrue(ConversionResult.Status.SUCCESS == result2.getStatus());

        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();
        NamespaceContext ctx = new IWXXMNamespaceContext();
        xpath.setNamespaceContext(ctx);

        Element output = result2.getConvertedMessage().map(Document::getDocumentElement).orElse(null);
        Element input = toValidate.getDocumentElement();
        assertNotNull(output);

        XPathExpression expr = xpath.compile("/iwxxm:TAF/iwxxm:issueTime/gml:TimeInstant/gml:timePosition");
        assertEquals("issueTime does not match", expr.evaluate(output), expr.evaluate(input));

        expr = xpath.compile("/iwxxm:TAF/iwxxm:validTime/gml:TimePeriod/gml:beginPosition");
        assertEquals("validTime begin position does not match", expr.evaluate(output), expr.evaluate(input));

        expr = xpath.compile("/iwxxm:TAF/iwxxm:validTime/gml:TimePeriod/gml:endPosition");
        assertEquals("validTime end position does not match", expr.evaluate(output), expr.evaluate(input));

        //Base forecast:

        //Type:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:type/@xlink:href");
        assertEquals("Base forecast type does not match", expr.evaluate(output),
                expr.evaluate(input));

        //Temporals:

        expr = xpath.compile("count(/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:phenomenonTime[@xlink:href = concat('#', /iwxxm:TAF/iwxxm:validTime/gml:TimePeriod/@gml:id)]) = 1");
        assertEquals("Base forecast phenomenonTime does not refer to msg validTime", expr.evaluate(output), expr.evaluate(input));

        expr = xpath.compile("count(/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:resultTime[@xlink:href = concat('#', /iwxxm:TAF/iwxxm:issueTime/gml:TimeInstant/@gml:id)]) = 1");
        assertEquals("Base forecast resultTime does not refer to msg issueTime", expr.evaluate(output), expr.evaluate(input));

        expr = xpath.compile("count(/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:validTime[@xlink:href = concat('#', /iwxxm:TAF/iwxxm:validTime/gml:TimePeriod/@gml:id)]) = 1");
        assertEquals("Base forecast validTime does not refer to msg validTime", expr.evaluate(output), expr.evaluate(input));


        //Procedure:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:procedure/metce:Process/gml:description");
        assertEquals("Process description does not match", expr.evaluate(output), expr.evaluate(input));


        //Observed property:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:observedProperty/@xlink:href");
        assertEquals("Observed properties does not match", expr.evaluate(output), expr.evaluate(input));

        //Aerodrome FOI (samplingFeature):


        expr = xpath.compile("/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:featureOfInterest/sams:SF_SpatialSamplingFeature/sam:sampledFeature/aixm"
                + ":AirportHeliport/aixm:timeSlice/aixm:AirportHeliportTimeSlice/aixm:designator");
        assertEquals("Airport designator does not match", expr.evaluate(output), expr.evaluate(input));

        expr = xpath.compile("/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:featureOfInterest/sams:SF_SpatialSamplingFeature/sam:sampledFeature/aixm"
                + ":AirportHeliport/aixm:timeSlice/aixm:AirportHeliportTimeSlice/aixm:name");
        assertEquals("Airport name does not match", expr.evaluate(output), expr.evaluate(input));

        expr = xpath.compile("/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:featureOfInterest/sams:SF_SpatialSamplingFeature/sam:sampledFeature/aixm"
                + ":AirportHeliport/aixm:timeSlice/aixm:AirportHeliportTimeSlice/aixm:locationIndicatorICAO");
        assertEquals("Airport ICAO code does not match", expr.evaluate(output), expr.evaluate(input));


        expr = xpath.compile("/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:featureOfInterest/sams:SF_SpatialSamplingFeature/sam:sampledFeature/aixm"
                + ":AirportHeliport/aixm:timeSlice/aixm:AirportHeliportTimeSlice/aixm:fieldElevation");
        assertEquals("Airport elevation value does not match", expr.evaluate(output), expr.evaluate(input));

        expr = xpath.compile("/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:featureOfInterest/sams:SF_SpatialSamplingFeature/sam:sampledFeature/aixm"
                + ":AirportHeliport/aixm:timeSlice/aixm:AirportHeliportTimeSlice/aixm:fieldElevation/@uom");
        assertEquals("Airport elevation unit does not match", expr.evaluate(output), expr.evaluate(input));



        //Sampling point:

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:featureOfInterest/sams:SF_SpatialSamplingFeature/sams:shape/gml:Point/@srsName");
        assertEquals("Sampling point srsName does not match", expr.evaluate(output), expr.evaluate(input));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:featureOfInterest/sams:SF_SpatialSamplingFeature/sams:shape/gml:Point/gml:pos");
        assertEquals("Sampling point position value does not match", expr.evaluate(output), expr.evaluate(input));


        //Forecast properties:

        //CAVOK:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/@cloudAndVisibilityOK");
        assertEquals("CAVOK does not match",  expr.evaluate(output), expr.evaluate(input));

        //Wind:
        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:surfaceWind/iwxxm:AerodromeSurfaceWindForecast"
                        + "/@variableWindDirection");
        assertEquals("Variable wind not match",  expr.evaluate(output), expr.evaluate(input));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:surfaceWind/iwxxm:AerodromeSurfaceWindForecast"
                        + "/iwxxm:meanWindDirection/@uom");
        assertEquals("Mean wind direction uom does not match", expr.evaluate(output), expr.evaluate(input));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:surfaceWind/iwxxm:AerodromeSurfaceWindForecast"
                        + "/iwxxm:meanWindDirection");
        assertTrue("Mean wind direction does not match", Math.abs(Double.parseDouble(expr.evaluate(output)) - Double.parseDouble(expr.evaluate(input))) < 0.00001);

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:surfaceWind/iwxxm:AerodromeSurfaceWindForecast"
                        + "/iwxxm:meanWindSpeed/@uom");
        assertEquals("Mean wind speed uom does not match", expr.evaluate(output), expr.evaluate(input));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:surfaceWind/iwxxm:AerodromeSurfaceWindForecast"
                        + "/iwxxm:meanWindSpeed");
        assertTrue("Mean wind speed does not match", Math.abs(Double.parseDouble(expr.evaluate(output)) - Double.parseDouble(expr.evaluate(input))) < 0.00001);
        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:surfaceWind/iwxxm:AerodromeSurfaceWindForecast"
                        + "/iwxxm:windGustSpeed/@uom");
        assertEquals("Wind gust speed uom does not match", expr.evaluate(output), expr.evaluate(input));


        //Visibility:
        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:prevailingVisibility");
        assertTrue("Visibility does not match", Math.abs(Double.parseDouble(expr.evaluate(output)) - Double.parseDouble(expr.evaluate(input))) < 0.00001);

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:prevailingVisibility/@uom");
        assertEquals("Visibility uom does not match", expr.evaluate(output), expr.evaluate(input));

        //Clouds:
        expr = xpath.compile(
                "count(/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm:AerodromeCloudForecast/iwxxm:layer)");
        assertEquals("Cloud layer count does not match", expr.evaluate(output), expr.evaluate(input));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm:AerodromeCloudForecast/iwxxm:layer[1]/iwxxm"
                        + ":CloudLayer/iwxxm:amount/@xlink:href");
        assertEquals("Cloud layer 1 amount does not match", expr.evaluate(output), expr.evaluate(input));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm:AerodromeCloudForecast/iwxxm:layer[1]/iwxxm"
                        + ":CloudLayer/iwxxm:base/@uom");
        assertEquals("Cloud layer 1 base uom does not match", expr.evaluate(output), expr.evaluate(input));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm:AerodromeCloudForecast/iwxxm:layer[1]/iwxxm"
                        + ":CloudLayer/iwxxm:base");
        assertTrue("Cloud layer 1 base does not match", Math.abs(Double.parseDouble(expr.evaluate(output)) - Double.parseDouble(expr.evaluate(input))) < 0.00001);



        //Change forecast 1:

        //Type:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:type/@xlink:href");
        assertEquals("Change forecast 1 type does not match", expr.evaluate(output), expr.evaluate(input));

        //Temporals:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:phenomenonTime/gml:TimePeriod/gml:beginPosition");
        assertEquals("Change forecast 1 phenomenonTime begin pos does not match",  expr.evaluate(output), expr.evaluate(input));

        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:phenomenonTime/gml:TimePeriod/gml:endPosition");
        assertEquals("Change forecast 1 phenomenonTime end pos does not match",  expr.evaluate(output), expr.evaluate(input));


        expr = xpath.compile("count(/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:resultTime[@xlink:href = concat('#', /iwxxm:TAF/iwxxm:issueTime/gml:TimeInstant/@gml:id)]) = 1");
        assertEquals("Change forecast 1 resultTime does not refer to msg issueTime", expr.evaluate(output), expr.evaluate(input));

        expr = xpath.compile("count(/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:validTime[@xlink:href = concat('#', /iwxxm:TAF/iwxxm:validTime/gml:TimePeriod/@gml:id)]) = 1");
        assertEquals("Change forecast 1 validTime does not refer to msg validTime", expr.evaluate(output), expr.evaluate(input));

        //Procedure:
        expr = xpath.compile("count(/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:procedure[@xlink:href = concat('#', /iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:procedure/metce:Process/@gml:id)]) = 1");
        assertEquals("Change forecast 1 Procedure does not refer to base forecast procedure",
                expr.evaluate(output), expr.evaluate(input));

        //Observed property:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:observedProperty/@xlink:href");
        assertEquals("Change forecast 1 Observed properties does not match", expr.evaluate(output), expr.evaluate(input));


        //FOI reference:
        expr = xpath.compile("count(/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:featureOfInterest[@xlink:href = concat('#', /iwxxm:TAF/iwxxm:baseForecast/om:OM_Observation/om:featureOfInterest/@gml:id)]) = 1");
        assertEquals("Change forecast 1 FOI reference does not point to base forecast FOI", expr.evaluate(output), expr.evaluate(input));

        //Change indicator:
        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/@changeIndicator");
        assertEquals("Change forecast 1 change indicator does not match", expr.evaluate(output), expr.evaluate(input));

        //CAVOK:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/@cloudAndVisibilityOK");
        assertEquals("Change forecast 1 CAVOK does not match", expr.evaluate(output), expr.evaluate(input));

        //FIXME: test the rest of the properties
        //Forecast properties:
/*
        //Visibility:
        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:prevailingVisibility");
        assertTrue("Change forecast 1 Visibility does not match", Math.abs(Double.parseDouble(expr.evaluate(docElement)) - 3000.0) < 0.00001);

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:prevailingVisibility/@uom");
        assertEquals("Change forecast 1 visibility uom does not match", "m", expr.evaluate(docElement));

        //Weather:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:weather[1]"
                + "/@xlink:href");
        assertEquals("Change forecast 1 weather 1 does not match", "http://codes.wmo.int/306/4678/RADZ" , expr.evaluate(docElement));

        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:weather[2]"
                + "/@xlink:href");
        assertEquals("Change forecast 1 weather 2 does not match", "http://codes.wmo.int/306/4678/BR" , expr.evaluate(docElement));

        //Clouds:
        expr = xpath.compile(
                "count(/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer)");
        assertEquals("Change Forecast 1 cloud layer count does not match", "1", expr.evaluate(docElement));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer[1]/iwxxm"
                        + ":CloudLayer/iwxxm:amount/@xlink:href");
        assertEquals("Change Forecast 1 cloud layer 1 amount does not match", "http://codes.wmo.int/bufr4/codeflag/0-20-008/4", expr.evaluate(docElement));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm:AerodromeCloudForecast/iwxxm:layer[1]/iwxxm"
                        + ":CloudLayer/iwxxm:base/@uom");
        assertEquals("Change Forecast 1 cloud layer 1 base uom does not match", "[ft_i]", expr.evaluate(docElement));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[1]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm:AerodromeCloudForecast/iwxxm:layer[1]/iwxxm"
                        + ":CloudLayer/iwxxm:base");
        assertTrue("Change Forecast 1 cloud layer 1 base does not match", Math.abs(Double.parseDouble(expr.evaluate(docElement)) - 400.0) < 0.00001);



        //Change forecast 2: BECMG 3018/3020 BKN008 SCT015CB

        //Type:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:type/@xlink:href");
        assertEquals("Change forecast 2 type does not match", "http://codes.wmo" + ".int/49-2/observation-type/iwxxm/2.1/MeteorologicalAerodromeForecast",
                expr.evaluate(docElement));

        //Temporals:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:phenomenonTime/gml:TimePeriod/gml:beginPosition");
        timeRef = expr.evaluate(docElement);
        assertEquals("Change forecast 2 phenomenonTime begin pos does not match", "2017-07-30T18:00:00Z", timeRef);

        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:phenomenonTime/gml:TimePeriod/gml:endPosition");
        timeRef = expr.evaluate(docElement);
        assertEquals("Change forecast 2 phenomenonTime end pos does not match", "2017-07-30T20:00:00Z", timeRef);

        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:resultTime/@xlink:href");
        timeRef = expr.evaluate(docElement);
        assertEquals("Change forecast 2 resultTime does not refer to msg issueTime", timeRef, "#" + issueTimeId);

        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:validTime/@xlink:href");
        timeRef = expr.evaluate(docElement);
        assertEquals("Change forecast 2 validTime does not refer to msg validTime", timeRef, "#" + validTimeId);

        //Procedure:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:procedure/@xlink:href");
        assertEquals("Change forecast 2 Procedure does not refer to base forecast procedure",
                "#" + procedureId,
                expr.evaluate(docElement));

        //Observed property:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:observedProperty/@xlink:href");
        assertEquals("Change forecast 2 observed properties does not match", "http://codes.wmo.int/49-2/observable-property/MeteorologicalAerodromeForecast",
                expr.evaluate(docElement));

        //FOI reference:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:featureOfInterest/@xlink:href");
        assertEquals("Change forecast 2 FOI reference does not point to base forecast FOI", "#" + foiId, expr.evaluate(docElement));

        //Change indicator:
        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/@changeIndicator");
        assertEquals("Change forecast 2 change indicator does not match", "BECOMING", expr.evaluate(docElement));

        //CAVOK:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord"
                + "/@cloudAndVisibilityOK");
        assertEquals("CAVOK does not match", "false", expr.evaluate(docElement));

        //Forecast properties:

        //Clouds:
        expr = xpath.compile(
                "count(/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer)");
        assertEquals("Change Forecast 2 cloud layer count does not match", "2", expr.evaluate(docElement));

        //BKN008
        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer[1]/iwxxm"
                        + ":CloudLayer/iwxxm:amount/@xlink:href");
        assertEquals("Change Forecast 2 cloud layer 1 amount does not match", "http://codes.wmo.int/bufr4/codeflag/0-20-008/3", expr.evaluate(docElement));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer[1]/iwxxm"
                        + ":CloudLayer/iwxxm:base/@uom");
        assertEquals("Change Forecast 2 cloud layer 1 base uom does not match", "[ft_i]", expr.evaluate(docElement));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer[1]/iwxxm"
                        + ":CloudLayer/iwxxm:base");
        assertTrue("Change Forecast 2 cloud layer 1 base does not match", Math.abs(Double.parseDouble(expr.evaluate(docElement)) - 800.0) < 0.00001);

        //SCT015CB
        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer[2]/iwxxm"
                        + ":CloudLayer/iwxxm:amount/@xlink:href");
        assertEquals("Change Forecast 2 cloud layer 2 amount does not match", "http://codes.wmo.int/bufr4/codeflag/0-20-008/2", expr.evaluate(docElement));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer[2]/iwxxm"
                        + ":CloudLayer/iwxxm:base/@uom");
        assertEquals("Change Forecast 2 cloud layer 2 base uom does not match", "[ft_i]", expr.evaluate(docElement));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer[2]/iwxxm"
                        + ":CloudLayer/iwxxm:base");
        assertTrue("Change Forecast 2 cloud layer 2 base does not match", Math.abs(Double.parseDouble(expr.evaluate(docElement)) - 1500.0) < 0.00001);

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[2]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer[2]/iwxxm"
                        + ":CloudLayer/iwxxm:cloudType/@xlink:href");
        assertEquals("Change Forecast 2 cloud layer 2 type does not match", "http://codes.wmo.int/bufr4/codeflag/0-20-012/9", expr.evaluate(docElement));


        //Change forecast 3: TEMPO 3102/3112 3000 SHRASN BKN006 BKN015CB

        //Type:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:type/@xlink:href");
        assertEquals("Change forecast 3 type does not match", "http://codes.wmo" + ".int/49-2/observation-type/iwxxm/2.1/MeteorologicalAerodromeForecast",
                expr.evaluate(docElement));

        //Temporals:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:phenomenonTime/gml:TimePeriod/gml:beginPosition");
        timeRef = expr.evaluate(docElement);
        assertEquals("Change forecast 3 phenomenonTime begin pos does not match", "2017-07-31T02:00:00Z", timeRef);

        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:phenomenonTime/gml:TimePeriod/gml:endPosition");
        timeRef = expr.evaluate(docElement);
        assertEquals("Change forecast 3 phenomenonTime end pos does not match", "2017-07-31T12:00:00Z", timeRef);

        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:resultTime/@xlink:href");
        timeRef = expr.evaluate(docElement);
        assertEquals("Change forecast 3 resultTime does not refer to msg issueTime", timeRef, "#" + issueTimeId);

        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:validTime/@xlink:href");
        timeRef = expr.evaluate(docElement);
        assertEquals("Change forecast 3 validTime does not refer to msg validTime", timeRef, "#" + validTimeId);

        //Procedure:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:procedure/@xlink:href");
        assertEquals("Change forecast 3 procedure does not refer to base forecast procedure",
                "#" + procedureId,
                expr.evaluate(docElement));

        //Observed property:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:observedProperty/@xlink:href");
        assertEquals("Change forecast 3 observed properties does not match", "http://codes.wmo.int/49-2/observable-property/MeteorologicalAerodromeForecast",
                expr.evaluate(docElement));

        //FOI reference:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:featureOfInterest/@xlink:href");
        assertEquals("Change forecast 3 FOI reference does not point to base forecast FOI", "#" + foiId, expr.evaluate(docElement));

        //Change indicator:
        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/@changeIndicator");
        assertEquals("Change forecast 3 change indicator does not match", "TEMPORARY_FLUCTUATIONS", expr.evaluate(docElement));

        //CAVOK:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord"
                + "/@cloudAndVisibilityOK");
        assertEquals("Change forecast 3 CAVOK does not match", "false", expr.evaluate(docElement));

        //Forecast properties:

        //Visibility:
        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:prevailingVisibility");
        assertTrue("Change forecast 3 Visibility does not match", Math.abs(Double.parseDouble(expr.evaluate(docElement)) - 3000.0) < 0.00001);

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:prevailingVisibility/@uom");
        assertEquals("Change forecast 3 visibility uom does not match", "m", expr.evaluate(docElement));

        //Weather:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:weather[1]"
                + "/@xlink:href");
        assertEquals("Change forecast 3 weather 1 does not match", "http://codes.wmo.int/306/4678/SHRASN" , expr.evaluate(docElement));

        //Cloud:

        //BKN006
        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer[1]/iwxxm"
                        + ":CloudLayer/iwxxm:amount/@xlink:href");
        assertEquals("Change Forecast 3 cloud layer 1 amount does not match", "http://codes.wmo.int/bufr4/codeflag/0-20-008/3", expr.evaluate(docElement));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer[1]/iwxxm"
                        + ":CloudLayer/iwxxm:base/@uom");
        assertEquals("Change Forecast 3 cloud layer 1 base uom does not match", "[ft_i]", expr.evaluate(docElement));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer[1]/iwxxm"
                        + ":CloudLayer/iwxxm:base");
        assertTrue("Change Forecast 3 cloud layer 1 base does not match", Math.abs(Double.parseDouble(expr.evaluate(docElement)) - 600.0) < 0.00001);

        //BKN015CB
        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer[2]/iwxxm"
                        + ":CloudLayer/iwxxm:amount/@xlink:href");
        assertEquals("Change Forecast 3 cloud layer 2 amount does not match", "http://codes.wmo.int/bufr4/codeflag/0-20-008/3", expr.evaluate(docElement));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer[2]/iwxxm"
                        + ":CloudLayer/iwxxm:base/@uom");
        assertEquals("Change Forecast 3 cloud layer 2 base uom does not match", "[ft_i]", expr.evaluate(docElement));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer[2]/iwxxm"
                        + ":CloudLayer/iwxxm:base");
        assertTrue("Change Forecast 3 cloud layer 2 base does not match", Math.abs(Double.parseDouble(expr.evaluate(docElement)) - 1500.0) < 0.00001);

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[3]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                        + ":AerodromeCloudForecast/iwxxm:layer[2]/iwxxm"
                        + ":CloudLayer/iwxxm:cloudType/@xlink:href");
        assertEquals("Change Forecast 3 cloud layer 2 type does not match", "http://codes.wmo.int/bufr4/codeflag/0-20-012/9", expr.evaluate(docElement));

        //Change forecast 4: BECMG 3104/3106 21016G30KT VV001=

        //Type:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:type/@xlink:href");
        assertEquals("Change forecast 4 type does not match", "http://codes.wmo" + ".int/49-2/observation-type/iwxxm/2.1/MeteorologicalAerodromeForecast",
                expr.evaluate(docElement));

        //Temporals:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:phenomenonTime/gml:TimePeriod/gml:beginPosition");
        timeRef = expr.evaluate(docElement);
        assertEquals("Change forecast 4 phenomenonTime begin pos does not match", "2017-07-31T04:00:00Z", timeRef);

        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:phenomenonTime/gml:TimePeriod/gml:endPosition");
        timeRef = expr.evaluate(docElement);
        assertEquals("Change forecast 4 phenomenonTime end pos does not match", "2017-07-31T06:00:00Z", timeRef);

        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:resultTime/@xlink:href");
        timeRef = expr.evaluate(docElement);
        assertEquals("Change forecast 4 resultTime does not refer to msg issueTime", timeRef, "#" + issueTimeId);

        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:validTime/@xlink:href");
        timeRef = expr.evaluate(docElement);
        assertEquals("Change forecast 4 validTime does not refer to msg validTime", timeRef, "#" + validTimeId);

        //Procedure:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:procedure/@xlink:href");
        assertEquals("Change forecast 4 procedure does not refer to base forecast procedure",
                "#" + procedureId,
                expr.evaluate(docElement));

        //Observed property:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:observedProperty/@xlink:href");
        assertEquals("Change forecast 4 observed properties does not match", "http://codes.wmo.int/49-2/observable-property/MeteorologicalAerodromeForecast",
                expr.evaluate(docElement));

        //FOI reference:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:featureOfInterest/@xlink:href");
        assertEquals("Change forecast 4 FOI reference does not point to base forecast FOI", "#" + foiId, expr.evaluate(docElement));

        //Change indicator:
        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/@changeIndicator");
        assertEquals("Change forecast 4 change indicator does not match", "BECOMING", expr.evaluate(docElement));

        //CAVOK:
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord"
                + "/@cloudAndVisibilityOK");
        assertEquals("Change forecast 4 CAVOK does not match", "false", expr.evaluate(docElement));

        //Forecast properties:

        //Wind: 21016G30KT
        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:surfaceWind/iwxxm:AerodromeSurfaceWindForecast"
                        + "/@variableWindDirection");
        assertEquals("Change forecast 4 variable wind not match", "false", expr.evaluate(docElement));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:surfaceWind/iwxxm:AerodromeSurfaceWindForecast"
                        + "/iwxxm:meanWindDirection/@uom");
        assertEquals("Change forecast 4 Mean wind direction uom does not match", "deg", expr.evaluate(docElement));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:surfaceWind/iwxxm:AerodromeSurfaceWindForecast"
                        + "/iwxxm:meanWindDirection");
        assertTrue("Change forecast 4 Mean wind direction does not match", Math.abs(Double.parseDouble(expr.evaluate(docElement)) - 210.0) < 0.00001);

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:surfaceWind/iwxxm:AerodromeSurfaceWindForecast"
                        + "/iwxxm:meanWindSpeed/@uom");
        assertEquals("Change forecast 4 Mean wind speed uom does not match", "[kn_i]", expr.evaluate(docElement));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:surfaceWind/iwxxm:AerodromeSurfaceWindForecast"
                        + "/iwxxm:meanWindSpeed");
        assertTrue("Change forecast 4 Mean wind speed does not match", Math.abs(Double.parseDouble(expr.evaluate(docElement)) - 16.0) < 0.00001);

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:surfaceWind/iwxxm:AerodromeSurfaceWindForecast"
                        + "/iwxxm:windGustSpeed/@uom");
        assertEquals("Change forecast 4 Wind gust speed uom does not match", "[kn_i]", expr.evaluate(docElement));

        expr = xpath.compile(
                "/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:surfaceWind/iwxxm:AerodromeSurfaceWindForecast"
                        + "/iwxxm:windGustSpeed");
        assertTrue("Change forecast 4 Wind gust speed does not match", Math.abs(Double.parseDouble(expr.evaluate(docElement)) - 30.0) < 0.00001);

        //VV001
        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                + ":AerodromeCloudForecast/iwxxm:verticalVisibility");
        assertTrue("Change Forecast 4 cloud vertical visibility value does not match", Math.abs(Double.parseDouble(expr.evaluate(docElement)) - 100) < 0.00001);

        expr = xpath.compile("/iwxxm:TAF/iwxxm:changeForecast[4]/om:OM_Observation/om:result/iwxxm:MeteorologicalAerodromeForecastRecord/iwxxm:cloud/iwxxm"
                + ":AerodromeCloudForecast/iwxxm:verticalVisibility/@uom");
        assertEquals("Change Forecast 4 cloud vertical visibility uom does not match", "[ft_i]", expr.evaluate(docElement));
*/
    }


}