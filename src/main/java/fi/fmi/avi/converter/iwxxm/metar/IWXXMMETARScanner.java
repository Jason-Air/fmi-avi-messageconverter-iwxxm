package fi.fmi.avi.converter.iwxxm.metar;

import aero.aixm511.RunwayDirectionTimeSlicePropertyType;
import aero.aixm511.RunwayDirectionTimeSliceType;
import aero.aixm511.RunwayDirectionType;
import fi.fmi.avi.converter.ConversionHints;
import fi.fmi.avi.converter.ConversionIssue;
import fi.fmi.avi.converter.IssueList;
import fi.fmi.avi.converter.iwxxm.AbstractIWXXMScanner;
import fi.fmi.avi.converter.iwxxm.GenericReportProperties;
import fi.fmi.avi.converter.iwxxm.OMObservationProperties;
import fi.fmi.avi.converter.iwxxm.ReferredObjectRetrievalContext;
import fi.fmi.avi.model.*;
import fi.fmi.avi.model.immutable.NumericMeasureImpl;
import fi.fmi.avi.model.immutable.RunwayDirectionImpl;
import fi.fmi.avi.model.metar.immutable.*;
import icao.iwxxm21.*;
import net.opengis.om20.OMObservationPropertyType;
import net.opengis.om20.OMObservationType;

import javax.xml.bind.JAXBElement;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

;

/**
 * Created by rinne on 25/07/2018.
 */
public class IWXXMMETARScanner extends AbstractIWXXMScanner {

    public static List<ConversionIssue> collectMETARProperties(final MeteorologicalAerodromeObservationReportType input,
            final ReferredObjectRetrievalContext refCtx,
            final METARProperties properties, final ConversionHints hints) {
        IssueList retval = new IssueList();

        MeteorologicalAerodromeReportStatusType status = input.getStatus();
        if (status != null) {
            properties.set(METARProperties.Name.STATUS, AviationCodeListUser.MetarStatus.valueOf(status.name()));
        } else {
            retval.add(new ConversionIssue(ConversionIssue.Type.MISSING_DATA, "Status is not given"));
        }

        properties.set(METARProperties.Name.SPECI, Boolean.valueOf(input instanceof SPECIType));

        // report metadata
        GenericReportProperties meta = new GenericReportProperties(input);
        retval.addAll(collectReportMetadata(input, meta, hints));
        properties.set(METARProperties.Name.REPORT_METADATA, meta);

        if (MeteorologicalAerodromeReportStatusType.MISSING == status) {
            boolean missingFound = false;
            for (String nilReason : input.getObservation().getNilReason()) {
                if ("missing".equals(nilReason)) {
                    missingFound = true;
                }
            }
            if (!missingFound) {
                retval.add(ConversionIssue.Severity.WARNING, ConversionIssue.Type.MISSING_DATA,
                        "No nilReason 'missing' was found in result for " + "a missing METAR");
            }

            if (!input.getTrendForecast().isEmpty()) {
                retval.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.SYNTAX, "Missing METARs may not contain Trend forecasts");
            }
            return retval;
        }

        properties.set(METARProperties.Name.AUTOMATED, input.isAutomatedStation());

        // observation
        Optional<OMObservationType> observation = resolveProperty(input.getObservation(), OMObservationType.class, refCtx);
        if (observation.isPresent()) {
            OMObservationProperties obsProps = new OMObservationProperties(observation.get());
            retval.addAll(collectObservationProperties(observation.get(), refCtx, properties, obsProps, hints));
            properties.set(METARProperties.Name.OBSERVATION, obsProps);
        } else {
            retval.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA, "No Observation in METAR");
        }

        // trends
        for (OMObservationPropertyType obsProp : input.getTrendForecast()) {
            Optional<OMObservationType> trend = resolveProperty(obsProp, OMObservationType.class, refCtx);
            if (trend.isPresent()) {
                OMObservationProperties trendProps = new OMObservationProperties(trend.get());
                retval.addAll(collectTrendProperties(trend.get(), refCtx, trendProps, hints));
                properties.addToList(METARProperties.Name.TREND_FORECAST, trendProps);
            } else {
                retval.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA, "No Observation in METAR");
            }
        }

        return retval;
    }

    private static IssueList collectObservationProperties(final OMObservationType obs, final ReferredObjectRetrievalContext refCtx,
            final METARProperties metarProps, final OMObservationProperties properties, final ConversionHints hints) {
        IssueList retval = collectCommonObsMetadata(obs, refCtx, properties, "METAR Observation", hints);
        Optional<Aerodrome> aerodrome = properties.get(OMObservationProperties.Name.AERODROME, Aerodrome.class);
        Optional<MeteorologicalAerodromeObservationRecordType> obsRecord = getAerodromeObservationRecordResult(obs, refCtx);
        if (obsRecord.isPresent()) {
            ObservationRecordProperties obsProps = new ObservationRecordProperties(obsRecord.get());

            //Surface wind (M)
            AerodromeSurfaceWindPropertyType windProp = obsRecord.get().getSurfaceWind();
            if (windProp != null) {
                withSurfaceWindBuilderFor(windProp, refCtx, (builder) -> {
                    obsProps.set(ObservationRecordProperties.Name.SURFACE_WIND, builder.build());
                }, retval::add);
            } else {
                retval.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA, "Surface wind is missing in non-missing METAR Observation");
            }

            // Air temperature (M)
            Optional<NumericMeasure> airTemp = asNumericMeasure(obsRecord.get().getAirTemperature());
            if (airTemp.isPresent()) {
                obsProps.set(ObservationRecordProperties.Name.AIR_TEMPERATURE, airTemp.get());
            } else {
                retval.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA, "Air temperature is missing in non-missing METAR Observation");
            }

            //Dew point temperature (M)
            Optional<NumericMeasure> dewpointTemp = asNumericMeasure(obsRecord.get().getDewpointTemperature());
            if (dewpointTemp.isPresent()) {
                obsProps.set(ObservationRecordProperties.Name.DEWPOINT_TEMPERATURE, dewpointTemp.get());
            } else {
                retval.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA,
                        "Dew point temperature is missing in non-missing METAR " + "Observation");
            }

            //QNH (M)
            Optional<NumericMeasure> qnh = asNumericMeasure(obsRecord.get().getQnh());
            if (qnh.isPresent()) {
                obsProps.set(ObservationRecordProperties.Name.QNH, qnh.get());
            } else {
                retval.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA, "QNH is missing in non-missing METAR Observation");
            }

            //Recent weather (C)
            for (AerodromeRecentWeatherType weather:obsRecord.get().getRecentWeather()) {
                withWeatherBuilderFor(weather, hints, (builder) -> {
                    obsProps.addToList(ObservationRecordProperties.Name.RECENT_WEATHER, builder.build());
                }, retval::add);
            }

            //wind shear (C)
            AerodromeWindShearPropertyType wsProp = obsRecord.get().getWindShear();
            if (wsProp != null) {
                withWindShearBuilderFor(wsProp, aerodrome, refCtx, (wsBuilder) -> {
                    obsProps.set(ObservationRecordProperties.Name.WIND_SHEAR, wsBuilder.build());
                }, retval::add);
            }

            //sea state (C)
            AerodromeSeaStatePropertyType ssProp = obsRecord.get().getSeaState();
            if (ssProp != null) {
                withSeaStateBuilderFor(ssProp, refCtx, (ssBuilder) -> {
                    obsProps.set(ObservationRecordProperties.Name.SEA_STATE, ssBuilder.build());
                }, retval::add);
            }

            // runway state (C)
            boolean snoclo = false;
            for (AerodromeRunwayStatePropertyType rvsProp : obsRecord.get().getRunwayState()) {
                if (rvsProp.getAerodromeRunwayState().isSnowClosure()) {
                    snoclo = true;
                }
                withRunwayStateBuilderFor(rvsProp, aerodrome, refCtx, (rvsBuilder) -> {
                    obsProps.addToList(ObservationRecordProperties.Name.RUNWAY_STATE, rvsBuilder.build());
                }, retval::add);
            }
            if (snoclo) {
                if (!obsProps.getList(ObservationRecordProperties.Name.RUNWAY_STATE, RunwayDirection.class).isEmpty()) {
                    obsProps.unset(ObservationRecordProperties.Name.RUNWAY_STATE);
                    retval.add(ConversionIssue.Severity.WARNING, ConversionIssue.Type.LOGICAL, "Additional runway state information given in addition to "
                            + "snow closure (applying for the entire aerodrome), discarded all runway states");
                }
                metarProps.set(METARProperties.Name.SNOW_CLOSURE, Boolean.TRUE);
            }

            //CAVOK:
            obsProps.set(ObservationRecordProperties.Name.CLOUD_AND_VISIBILITY_OK, obsRecord.get().isCloudAndVisibilityOK());
            if (obsRecord.get().isCloudAndVisibilityOK()) {
                // visibility, RVR,  present weather, cloud not allowed with CAVOK
                if (obsRecord.get().getVisibility() != null && obsRecord.get().getVisibility().getAerodromeHorizontalVisibility() != null) {
                    retval.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.SYNTAX, "Visibility is not empty with CAVOK");
                }

                if (!obsRecord.get().getRvr().isEmpty()) {
                    retval.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.SYNTAX, "Runway Visual Range is not empty with CAVOK");
                }

                if (!obsRecord.get().getPresentWeather().isEmpty()) {
                    retval.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.SYNTAX, "Present weather is not empty with CAVOK");
                }

                if (obsRecord.get().getCloud() != null && !obsRecord.get().getCloud().isNil()) {
                    retval.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.SYNTAX, "Cloud is not empty with CAVOK");
                }

            } else {
                //Horizontal visibility (M)
                AerodromeHorizontalVisibilityPropertyType visProp = obsRecord.get().getVisibility();
                if (visProp != null) {
                    withVisibilityBuilderFor(visProp, refCtx, (builder) -> {
                        obsProps.set(ObservationRecordProperties.Name.VISIBILITY, builder.build());
                    }, retval::add);
                } else {
                    retval.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA, "Horizontal visibility missing in METAR Observation");
                }

                //RVR (C)
                for (AerodromeRunwayVisualRangePropertyType rvrProp : obsRecord.get().getRvr()) {
                    withRunwayVisualRangeBuilderFor(rvrProp, aerodrome, refCtx, (rvrBuilder) -> {
                        obsProps.addToList(ObservationRecordProperties.Name.RUNWAY_VISUAL_RANGE, rvrBuilder.build());
                    }, retval::add);
                }

                // Present weather (C)
                for (AerodromePresentWeatherType weather:obsRecord.get().getPresentWeather()) {
                    withWeatherBuilderFor(weather, hints, (builder) -> {
                        obsProps.addToList(ObservationRecordProperties.Name.PRESENT_WEATHER, builder.build());
                    }, retval::add);
                }

                // Cloud (M)
                JAXBElement<MeteorologicalAerodromeObservationRecordType.Cloud> cloudElement = obsRecord.get().getCloud();
                if (cloudElement != null) {
                    withObservedCloudBuilderFor(cloudElement, refCtx, (cloudBuilder) -> {
                        obsProps.set(ObservationRecordProperties.Name.CLOUD, cloudBuilder.build());
                    }, retval::add);
                }
            }
        } else {
            retval.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA, "No observation result record in METAR Observation");
        }
        return retval;
    }

    private static IssueList collectTrendProperties(final OMObservationType trend, final ReferredObjectRetrievalContext refCtx,
            final OMObservationProperties properties, final ConversionHints hints) {
        IssueList retval = collectCommonObsMetadata(trend, refCtx, properties, "METAR Trend", hints);
        Optional<MeteorologicalAerodromeTrendForecastRecordType> trendRecord = getAerodromeTrendRecordResult(trend, refCtx);
        if (trendRecord.isPresent()) {
            TrendForecastRecordProperties trendProps = new TrendForecastRecordProperties(trendRecord.get());
            //TODO: populate

        } else {
            retval.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA, "No observation result record in METAR Trend");
        }
        return retval;
    }

    private static void withSurfaceWindBuilderFor(final AerodromeSurfaceWindPropertyType windProp, final ReferredObjectRetrievalContext refCtx,
            final Consumer<ObservedSurfaceWindImpl.Builder> resultHandler, final Consumer<ConversionIssue> issueHandler) {
        ConversionIssue issue = null;
        Optional<AerodromeSurfaceWindType> wind = resolveProperty(windProp, AerodromeSurfaceWindType.class, refCtx);
        if (wind.isPresent()) {
            ObservedSurfaceWindImpl.Builder windBuilder = new ObservedSurfaceWindImpl.Builder();
            windBuilder.setMeanWindDirection(asNumericMeasure(wind.get().getMeanWindDirection()));
            windBuilder.setVariableDirection(wind.get().isVariableWindDirection());
            if (wind.get().getMeanWindSpeed() != null) {
                windBuilder.setMeanWindSpeed(asNumericMeasure(wind.get().getMeanWindSpeed()).get());
            } else {
                issue = new ConversionIssue(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA,
                        "Mean wind speed missing from METAR surface " + "wind observation");
            }
            windBuilder.setMeanWindSpeedOperator(asRelationalOperator(wind.get().getMeanWindSpeedOperator()));
            windBuilder.setWindGust(asNumericMeasure(wind.get().getWindGustSpeed()));
            windBuilder.setWindGustOperator(asRelationalOperator(wind.get().getWindGustSpeedOperator()));
            windBuilder.setExtremeClockwiseWindDirection(asNumericMeasure(wind.get().getExtremeClockwiseWindDirection()));
            windBuilder.setExtremeCounterClockwiseWindDirection(asNumericMeasure(wind.get().getExtremeCounterClockwiseWindDirection()));
            if (issue == null) {
                resultHandler.accept(windBuilder);
            }
        } else {
            issue = new ConversionIssue(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA,
                    "Could not find AerodromeSurfaceWindType value within " + "AerodromeSurfaceWindPropertyType or by reference");
        }
        if (issue != null) {
            issueHandler.accept(issue);
        }
    }

    private static void withVisibilityBuilderFor(final AerodromeHorizontalVisibilityPropertyType visProp, final ReferredObjectRetrievalContext refCtx, final Consumer<HorizontalVisibilityImpl.Builder> resultHandler, final Consumer<ConversionIssue> issueHandler) {
        ConversionIssue issue = null;
        Optional<AerodromeHorizontalVisibilityType> visibility = resolveProperty(visProp, AerodromeHorizontalVisibilityType.class, refCtx);
        if (visibility.isPresent()) {
            HorizontalVisibilityImpl.Builder visBuilder = new HorizontalVisibilityImpl.Builder();
            if (visibility.get().getPrevailingVisibility() != null) {
                visBuilder.setPrevailingVisibility(asNumericMeasure(visibility.get().getPrevailingVisibility()).get());
            } else {
                issue = new ConversionIssue(ConversionIssue.Type.MISSING_DATA, "Prevailing visibility missing from METAR horizontal visibility observation");
            }
            visBuilder.setPrevailingVisibilityOperator(asRelationalOperator(visibility.get().getPrevailingVisibilityOperator()));
            visBuilder.setMinimumVisibility(asNumericMeasure(visibility.get().getMinimumVisibility()));
            if (issue == null) {
                resultHandler.accept(visBuilder);
            }
        } else {
            issue = new ConversionIssue(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA,
                    "Could not find AerodromeHorizontalVisibilityType value within " + "AerodromeHorizontalVisibilityPropertyType or by reference");
        }
        if (issue != null) {
            issueHandler.accept(issue);
        }
    }

    private static void withWindShearBuilderFor(final AerodromeWindShearPropertyType shearProp, final Optional<Aerodrome> aerodrome,
            final ReferredObjectRetrievalContext refCtx, final Consumer<WindShearImpl.Builder> resultHandler, final Consumer<ConversionIssue> issueHandler) {
        IssueList issues = new IssueList();
        Optional<AerodromeWindShearType> windShear = resolveProperty(shearProp, AerodromeWindShearType.class, refCtx);
        if (windShear.isPresent()) {
            WindShearImpl.Builder wsBuilder = new WindShearImpl.Builder();
            if (windShear.get().isAllRunways()) {
                wsBuilder.setAppliedToAllRunways(windShear.get().isAllRunways());
                if (!windShear.get().getRunway().isEmpty()) {
                    issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.LOGICAL,
                            "WindShear has flag 'allRunways' but also contains a " + "non-empty list of runway elements, ignoring the individual runways");
                }
            } else {
                List<RunwayDirection> directions = new ArrayList<>();
                for (RunwayDirectionPropertyType rwdProp : windShear.get().getRunway()) {
                    withRunwayDirectionBuilderFor(rwdProp, aerodrome, refCtx, (rwdBuilder) -> {
                        directions.add(rwdBuilder.build());
                    }, issues::add);
                }
                wsBuilder.setRunwayDirections(directions);
            }
            resultHandler.accept(wsBuilder);
        } else {
            issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA,
                    "Could not resolve AerodromeWindShearType from within " + "AerodromeWindShearPropertyType");
        }
        for (ConversionIssue issue : issues) {
            issueHandler.accept(issue);
        }
    }

    private static void withSeaStateBuilderFor(final AerodromeSeaStatePropertyType seaStateProperty, final ReferredObjectRetrievalContext refCtx,
            final Consumer<SeaStateImpl.Builder> resultHandler, final Consumer<ConversionIssue> issueHandler) {
        IssueList issues = new IssueList();
        Optional<AerodromeSeaStateType> seaState = resolveProperty(seaStateProperty, AerodromeSeaStateType.class, refCtx);
        if (seaState.isPresent()) {
            SeaStateImpl.Builder ssBuilder = new SeaStateImpl.Builder();
            //Either temp AND (state OR sig wave height)
            if (seaState.get().getSeaSurfaceTemperature() != null) {
                ssBuilder.setSeaSurfaceTemperature(asNumericMeasure(seaState.get().getSeaSurfaceTemperature()).get());
            } else {
                issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA, "Sea surface temperature missing in SeaState");
            }
            if (seaState.get().getSeaState() != null && seaState.get().getSeaState().getHref() != null) {
                if (seaState.get().getSeaState().getHref().startsWith(AviationCodeListUser.CODELIST_VALUE_PREFIX_SEA_SURFACE_STATE)) {
                    String code = seaState.get().getSeaState().getHref().substring(AviationCodeListUser.CODELIST_VALUE_PREFIX_SEA_SURFACE_STATE.length());
                    ssBuilder.setSeaSurfaceState(AviationCodeListUser.SeaSurfaceState.fromInt(Integer.parseInt(code)));
                } else {
                    issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.SYNTAX,
                            "Invalid codelist value used for sea surface state, should start " + "with "
                                    + AviationCodeListUser.CODELIST_VALUE_PREFIX_SEA_SURFACE_STATE);
                }
                if (seaState.get().getSignificantWaveHeight() != null) {
                    issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.LOGICAL,
                            "Significant wave height not allowed with sea surface state in " + "SeaState");
                }
            } else if (seaState.get().getSignificantWaveHeight() != null) {
                ssBuilder.setSignificantWaveHeight(asNumericMeasure(seaState.get().getSignificantWaveHeight()));
            } else {
                issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA,
                        "One of sea surface state or significant wave height must be " + "given in SeaState");
            }
            resultHandler.accept(ssBuilder);
        } else {
            issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA,
                    "Could not resolve AerodromeSeaStateType from within " + "AerodromeSeaStatePropertyType");
        }
        for (ConversionIssue issue : issues) {
            issueHandler.accept(issue);
        }
    }

    private static void withRunwayStateBuilderFor(final AerodromeRunwayStatePropertyType rwsProp, final Optional<Aerodrome> aerodrome,
            final ReferredObjectRetrievalContext refCtx, final Consumer<RunwayStateImpl.Builder> resultHandler, final Consumer<ConversionIssue> issueHandler) {
        IssueList issues = new IssueList();
        Optional<AerodromeRunwayStateType> runwayState = resolveProperty(rwsProp, AerodromeRunwayStateType.class, refCtx);
        if (runwayState.isPresent()) {
            RunwayStateImpl.Builder rwsBuilder = new RunwayStateImpl.Builder();
            if (runwayState.get().isSnowClosure()) {
                //none of the other RVS parameters are allowed
                //runway direction not allowed
                if (runwayState.get().getRunway() != null || runwayState.get().isCleared() || runwayState.get().getDepositType() != null
                        || runwayState.get().getContamination() != null || runwayState.get().getEstimatedSurfaceFrictionOrBrakingAction() != null
                        || runwayState.get().getDepthOfDeposit() != null) {
                    issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.LOGICAL, "No runwayDirection, cleared, deposit, contamination, surface "
                            + "friction / braking action or depth of deposit allowed with snow closure flag");
                }
            } else {
                if (runwayState.get().isAllRunways()) {
                    rwsBuilder.setAppliedToAllRunways(true);
                    if (runwayState.get().getRunway() != null) {
                        issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.LOGICAL,
                                "Runway should not be given if 'allRunways' is true in " + "RunwayState");
                    }
                } else {
                    RunwayDirectionPropertyType rwdProp = runwayState.get().getRunway();
                    if (rwdProp != null) {
                        withRunwayDirectionBuilderFor(rwdProp, aerodrome, refCtx, (rwdBuilder) -> {
                            rwsBuilder.setRunwayDirection(rwdBuilder.build());
                        }, issues::add);
                    } else {
                        issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA,
                                "Runway designator must be given in RunwayState unless " + "'allRunways' flag is given");
                    }
                }
                rwsBuilder.setCleared(runwayState.get().isCleared());
                if (runwayState.get().isCleared()) {
                    //no other properties allowed
                    if (runwayState.get().getDepositType() != null || runwayState.get().getContamination() != null
                            || runwayState.get().getEstimatedSurfaceFrictionOrBrakingAction() != null || runwayState.get().getDepthOfDeposit() != null) {
                        issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.LOGICAL,
                                "No deposit, contamination, surface friction / braking " + "action or depth of deposit allowed with cleared flag");
                    }

                } else {
                    //deposit
                    if (runwayState.get().getDepositType() != null && runwayState.get().getDepositType().getHref() != null) {
                        if (runwayState.get().getDepositType().getHref().startsWith(AviationCodeListUser.CODELIST_VALUE_PREFIX_RUNWAY_DEPOSITS)) {
                            String code = runwayState.get()
                                    .getDepositType()
                                    .getHref()
                                    .substring(AviationCodeListUser.CODELIST_VALUE_PREFIX_RUNWAY_DEPOSITS.length());
                            rwsBuilder.setDeposit(AviationCodeListUser.RunwayDeposit.fromInt(Integer.parseInt(code)));
                        } else {
                            issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.SYNTAX,
                                    "Unknown codelist reference for runway state " + "deposit '" + runwayState.get().getDepositType().getHref() + "'");
                        }
                    }

                    //contamination
                    if (runwayState.get().getContamination() != null && runwayState.get().getContamination().getHref() != null) {
                        if (runwayState.get().getContamination().getHref().startsWith(AviationCodeListUser.CODELIST_VALUE_PREFIX_RUNWAY_CONTAMINATION)) {
                            String code = runwayState.get()
                                    .getContamination()
                                    .getHref()
                                    .substring(AviationCodeListUser.CODELIST_VALUE_PREFIX_RUNWAY_CONTAMINATION.length());
                            rwsBuilder.setContamination(AviationCodeListUser.RunwayContamination.fromInt(Integer.parseInt(code)));
                        } else {
                            issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.SYNTAX,
                                    "Unknown codelist reference for runway state " + "contamination '" + runwayState.get().getContamination().getHref() + "'");
                        }
                    }

                    //depth of deposit
                    if (runwayState.get().getDepthOfDeposit() != null) {
                        JAXBElement<DistanceWithNilReasonType> dod = runwayState.get().getDepthOfDeposit();
                        if (dod.isNil()) {
                            rwsBuilder.setDepthNotMeasurable(true);
                        } else if (dod.getValue() != null) {
                            if (dod.getValue().getNilReason().isEmpty()) {
                                rwsBuilder.setDepthOfDeposit(NumericMeasureImpl.of(dod.getValue().getValue(), dod.getValue().getUom()));
                            } else {
                                for (String nilReason : dod.getValue().getNilReason()) {
                                    if (AviationCodeListUser.CODELIST_VALUE_NIL_REASON_NOTHING_OF_OPERATIONAL_SIGNIFICANCE.equals(nilReason)) {
                                        rwsBuilder.setDepthInsignificant(true);
                                    } else if (AviationCodeListUser.CODELIST_VALUE_NIL_REASON_NOT_OBSERVABLE.equals(nilReason)) {
                                        rwsBuilder.setDepthNotMeasurable(true);
                                    } else {
                                        issues.add(ConversionIssue.Severity.WARNING, ConversionIssue.Type.SYNTAX,
                                                "Unknown nilReason codelist reference '" + nilReason + "'");
                                    }
                                }
                            }
                        }
                    }

                    //friction/braking action
                    if (runwayState.get().getEstimatedSurfaceFrictionOrBrakingAction() != null
                            && runwayState.get().getEstimatedSurfaceFrictionOrBrakingAction().getHref() != null) {
                        if (runwayState.get()
                                .getEstimatedSurfaceFrictionOrBrakingAction()
                                .getHref()
                                .startsWith(AviationCodeListUser.CODELIST_VALUE_PREFIX_RUNWAY_SURFACE_FRICTION_OR_BRAKING_ACTION)) {
                            String code = runwayState.get()
                                    .getEstimatedSurfaceFrictionOrBrakingAction()
                                    .getHref()
                                    .substring(AviationCodeListUser.CODELIST_VALUE_PREFIX_RUNWAY_SURFACE_FRICTION_OR_BRAKING_ACTION.length());
                            AviationCodeListUser.BrakingAction ba = AviationCodeListUser.BrakingAction.fromInt(Integer.parseInt(code));
                            if (ba == null) {
                                //not one of the BA codes, must be surface friction
                                int intValue = Integer.parseInt(code);
                                if (intValue == 127) {
                                    rwsBuilder.setRunwayNotOperational(true);
                                } else if (intValue == 99) {
                                    rwsBuilder.setEstimatedSurfaceFrictionUnreliable(true);
                                } else if (intValue >= 0 && intValue <= 98) {
                                    double friction = Integer.parseInt(code) / 100.0;
                                    rwsBuilder.setEstimatedSurfaceFriction(friction);
                                } else {
                                    issues.add(ConversionIssue.Severity.WARNING, ConversionIssue.Type.SYNTAX,
                                            "Unknown estimated surface friction / braking " + "action code '" + intValue + "'");
                                }
                            } else {
                                rwsBuilder.setBrakingAction(ba);
                            }
                        } else {
                            issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.SYNTAX,
                                    "Invalid codelist reference for " + "estimated surface friction / braking action '" + runwayState.get()
                                            .getEstimatedSurfaceFrictionOrBrakingAction()
                                            .getHref() + "'");
                        }
                    }

                }
            }
            resultHandler.accept(rwsBuilder);
        } else {
            issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA,
                    "Could not resolve AerodromeRunwayStateType from within " + "AerodromeRunwayStatePropertyType");
        }
        for (ConversionIssue issue : issues) {
            issueHandler.accept(issue);
        }
    }

    private static void withRunwayVisualRangeBuilderFor(final AerodromeRunwayVisualRangePropertyType rvrProp, final Optional<Aerodrome> aerodrome,
            final ReferredObjectRetrievalContext refCtx, final Consumer<RunwayVisualRangeImpl.Builder> resultHandler,
            final Consumer<ConversionIssue> issueHandler) {
        IssueList issues = new IssueList();
        //TODO

        for (ConversionIssue issue : issues) {
            issueHandler.accept(issue);
        }
    }

    private static void withObservedCloudBuilderFor(final JAXBElement<MeteorologicalAerodromeObservationRecordType.Cloud> cloudElement,
            final ReferredObjectRetrievalContext refCtx, final Consumer<ObservedCloudsImpl.Builder> resultHandler,
            final Consumer<ConversionIssue> issueHandler) {
        IssueList issues = new IssueList();
        if (cloudElement != null && cloudElement.getValue() != null) {
            MeteorologicalAerodromeObservationRecordType.Cloud cloud = cloudElement.getValue();
            if (cloud != null) {
                ObservedCloudsImpl.Builder cloudBuilder = new ObservedCloudsImpl.Builder();
                AerodromeObservedCloudsType obsClouds = cloud.getAerodromeObservedClouds();
                if (!cloud.getNilReason().isEmpty() && obsClouds != null) {
                    if (obsClouds.getVerticalVisibility() != null) {
                        JAXBElement<LengthWithNilReasonType> vv = obsClouds.getVerticalVisibility();
                        if (vv != null) {
                            if (vv.getValue() != null) {
                                LengthWithNilReasonType length = vv.getValue();
                                if (length.getNilReason().stream().anyMatch(AviationCodeListUser.CODELIST_VALUE_NIL_REASON_NOT_OBSERVABLE::equals)) {
                                    cloudBuilder.setVerticalVisibilityUnobservableByAutoSystem(true);
                                } else {
                                    cloudBuilder.setVerticalVisibility(asNumericMeasure(vv.getValue()));
                                }
                            } else {
                               issues.add(ConversionIssue.Severity.WARNING, ConversionIssue.Type.MISSING_DATA, "No value for vertical visibility element, strange");
                            }
                        }
                    } else if (!obsClouds.getLayer().isEmpty()) {
                        List<CloudLayer> layers = new ArrayList<>();
                        for (CloudLayerPropertyType layerProp: obsClouds.getLayer()) {
                            withCloudLayerBuilderFor(layerProp, refCtx, (layerBuilder) -> {
                                    layers.add(layerBuilder.build());
                                }, issues::add, "observed cloud");
                        }
                        cloudBuilder.setLayers(layers);
                    } else {
                        issues.add(ConversionIssue.Severity.WARNING, ConversionIssue.Type.MISSING_DATA, "ObservedClouds contains neither vertical visibility nor any cloud layers");
                    }
                } else {
                    cloudBuilder.setNoCloudsDetectedByAutoSystem(cloud.getNilReason().stream()
                            .anyMatch(AviationCodeListUser.CODELIST_VALUE_NIL_REASON_NOT_DETECTED_BY_AUTO_SYSTEM::equals));
                    cloudBuilder.setNoSignificantCloud(cloud.getNilReason().stream()
                            .anyMatch(AviationCodeListUser.CODELIST_VALUE_NIL_REASON_NOTHING_OF_OPERATIONAL_SIGNIFICANCE::equals));
                }
            }
        }
        for (ConversionIssue issue : issues) {
            issueHandler.accept(issue);
        }
    }

    private static void withRunwayDirectionBuilderFor(final RunwayDirectionPropertyType rwdProp, final Optional<Aerodrome> aerodrome,
            final ReferredObjectRetrievalContext refCtx, final Consumer<RunwayDirectionImpl.Builder> resultHandler,
            final Consumer<ConversionIssue> issueHandler) {
        IssueList issues = new IssueList();
        Optional<RunwayDirectionType> rwd = resolveProperty(rwdProp, RunwayDirectionType.class, refCtx);
        if (rwd.isPresent()) {
            List<RunwayDirectionTimeSlicePropertyType> slicePropList = rwd.get().getTimeSlice();
            if (slicePropList == null || slicePropList.isEmpty()) {
                issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA, "No timeSlices in RunwayDirection");
            } else {
                Optional<RunwayDirectionTimeSliceType> slice = Optional.empty();
                if (slicePropList.size() > 1) {
                    issues.add(ConversionIssue.Severity.WARNING, ConversionIssue.Type.SYNTAX,
                            "More than one timeSlice within " + "RunwayDirection, choosing the first SNAPSHOT");
                }
                for (RunwayDirectionTimeSlicePropertyType sliceProp : slicePropList) {
                    slice = resolveProperty(slicePropList.get(0), RunwayDirectionTimeSliceType.class, refCtx);
                    if (slice.isPresent() && "SNAPSHOT".equals(slice.get().getInterpretation())) {
                        break;
                    }
                }
                if (slice.isPresent()) {
                    RunwayDirectionImpl.Builder rwdBuilder = new RunwayDirectionImpl.Builder();
                    String designator = slice.get().getDesignator().getValue();
                    BigDecimal trueBearing = slice.get().getTrueBearing().getValue();
                    rwdBuilder.setAssociatedAirportHeliport(aerodrome);
                    rwdBuilder.setDesignator(designator);
                    if (trueBearing != null) {
                        rwdBuilder.setTrueBearing(trueBearing.doubleValue());
                    }
                    resultHandler.accept(rwdBuilder);
                } else {
                    issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA, "Unable to find " + "RunwayDirectionTimeSliceType");
                }
            }
        } else {
            issues.add(ConversionIssue.Severity.ERROR, ConversionIssue.Type.MISSING_DATA,
                    "Unable to resolve the RunwayDirectionType within " + "the runway element");
        }
        for (ConversionIssue issue : issues) {
            issueHandler.accept(issue);
        }
    }
}