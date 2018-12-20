/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.eptsreports.reporting.calculation.pvls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.calculation.patient.PatientCalculationContext;
import org.openmrs.calculation.result.CalculationResultMap;
import org.openmrs.calculation.result.SimpleResult;
import org.openmrs.module.eptsreports.metadata.CommonMetadata;
import org.openmrs.module.eptsreports.metadata.HivMetadata;
import org.openmrs.module.eptsreports.reporting.calculation.AbstractPatientCalculation;
import org.openmrs.module.eptsreports.reporting.calculation.EptsCalculations;
import org.openmrs.module.eptsreports.reporting.utils.EptsCalculationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Calculates the date on which a patient first started ART
 * 
 * @return a CulculationResultMap
 */
@Component
public class InitialArtStartDateCalculation extends AbstractPatientCalculation {
	
	@Autowired
	private HivMetadata hivMetadata;
	
	@Autowired
	private CommonMetadata commonMetadata;
	
	/**
	 * @should return null for patients who have not started ART
	 * @should return start date for patients who have started ART
	 * @see org.openmrs.calculation.patient.PatientCalculation#evaluate(java.util.Collection,
	 *      java.util.Map, org.openmrs.calculation.patient.PatientCalculationContext)
	 */
	@Override
	public CalculationResultMap evaluate(Collection<Integer> cohort, Map<String, Object> parameterValues,
	        PatientCalculationContext context) {
		
		// Get calculation map date for the first program enrollment
		CalculationResultMap map = new CalculationResultMap();
		
		Concept arvPlan = hivMetadata.getARVPlanConcept();
		Concept drugStartDate = commonMetadata.gethistoricalDrugStartDateConcept();
		Concept startDrugs = commonMetadata.getstartDrugsConcept();
		
		EncounterType encounterTypePharmacy = hivMetadata.getARVPharmaciaEncounterType();
		EncounterType adultoSeguimento = hivMetadata.getAdultoSeguimentoEncounterType();
		EncounterType arvPaed = hivMetadata.getARVPediatriaSeguimentoEncounterType();
		
		CalculationResultMap inProgramMap = calculate(Context.getRegisteredComponents(InHivProgramEnrollmentCalculation.class).get(0),
		    cohort, context);
		CalculationResultMap startDrugMap = EptsCalculations.firstObs(arvPlan, cohort, context);
		CalculationResultMap historicalMap = EptsCalculations.firstObs(drugStartDate, cohort, context);
		CalculationResultMap pharmacyEncounterMap = EptsCalculations.firstEncounter(encounterTypePharmacy, cohort, context);
		
		for (Integer pId : cohort) {
			Date dateEnrolledIntoProgram = null;
			Date dateStartedDrugs;
			Date historicalDate;
			Date pharmacyDate;
			Date requiredDate = null;
			List<Date> enrollmentDates = new ArrayList<Date>();
			SimpleResult result = (SimpleResult) inProgramMap.get(pId);
			if (result != null) {
				dateEnrolledIntoProgram = (Date) result.getValue();
				enrollmentDates.add(dateEnrolledIntoProgram);
			}
			Obs startDateObsResults = EptsCalculationUtils.obsResultForPatient(startDrugMap, pId);
			if (startDateObsResults != null && startDateObsResults.getValueCoded().equals(startDrugs)) {
				if (startDateObsResults.getEncounter().getEncounterType().equals(encounterTypePharmacy)
				        || startDateObsResults.getEncounter().getEncounterType().equals(adultoSeguimento)
				        || startDateObsResults.getEncounter().getEncounterType().equals(arvPaed)) {
					dateStartedDrugs = startDateObsResults.getObsDatetime();
					enrollmentDates.add(dateStartedDrugs);
				}
			}
			
			Obs historicalDateValue = EptsCalculationUtils.obsResultForPatient(historicalMap, pId);
			if (historicalDateValue != null && historicalDateValue.getEncounter() != null
			        && historicalDateValue.getEncounter().getEncounterType() != null
			        && historicalDateValue.getValueDatetime() != null) {
				
				if (historicalDateValue.getEncounter().getEncounterType().equals(encounterTypePharmacy)
				        || historicalDateValue.getEncounter().getEncounterType().equals(adultoSeguimento)
				        || historicalDateValue.getEncounter().getEncounterType().equals(arvPaed)) {
					historicalDate = historicalDateValue.getValueDatetime();
					enrollmentDates.add(historicalDate);
				}
			}
			
			Encounter pharmacyEncounter = EptsCalculationUtils.encounterResultForPatient(pharmacyEncounterMap, pId);
			if (pharmacyEncounter != null) {
				pharmacyDate = pharmacyEncounter.getEncounterDatetime();
				enrollmentDates.add(pharmacyDate);
			}
			
			if (enrollmentDates.size() > 0) {
				if (enrollmentDates.size() == 1) {
					requiredDate = enrollmentDates.get(0);
				} else if (enrollmentDates.size() == 2) {
					requiredDate = EptsCalculationUtils.earliest(enrollmentDates.get(0), enrollmentDates.get(1));
				} else if (enrollmentDates.size() == 3) {
					Date tempDate = EptsCalculationUtils.earliest(enrollmentDates.get(0), enrollmentDates.get(1));
					requiredDate = EptsCalculationUtils.earliest(enrollmentDates.get(2), tempDate);
				} else if (enrollmentDates.size() == 4) {
					Date tempDate1 = EptsCalculationUtils.earliest(enrollmentDates.get(0), enrollmentDates.get(1));
					Date tempDate2 = EptsCalculationUtils.earliest(enrollmentDates.get(2), enrollmentDates.get(3));
					requiredDate = EptsCalculationUtils.earliest(tempDate1, tempDate2);
				}
			}
			map.put(pId, new SimpleResult(requiredDate, this));
		}
		return map;
	}
}
