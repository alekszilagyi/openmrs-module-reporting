package org.openmrs.module.reporting.web.controller;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Cohort;
import org.openmrs.Location;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.cohort.definition.CohortDefinition;
import org.openmrs.module.cohort.definition.service.CohortDefinitionService;
import org.openmrs.module.dataset.definition.CohortIndicatorDataSetDefinition;
import org.openmrs.module.dataset.definition.DataSetDefinition;
import org.openmrs.module.dataset.definition.service.DataSetDefinitionService;
import org.openmrs.module.evaluation.EvaluationContext;
import org.openmrs.module.evaluation.parameter.Mapped;
import org.openmrs.module.evaluation.parameter.Parameter;
import org.openmrs.module.indicator.CohortIndicator;
import org.openmrs.module.indicator.Indicator;
import org.openmrs.module.indicator.service.IndicatorService;
import org.openmrs.module.report.IndicatorReportDefinition;
import org.openmrs.module.report.PeriodIndicatorReportDefinition;
import org.openmrs.module.report.ReportData;
import org.openmrs.module.report.ReportDefinition;
import org.openmrs.module.report.renderer.CsvReportRenderer;
import org.openmrs.module.report.renderer.ReportRenderer;
import org.openmrs.module.report.renderer.TsvReportRenderer;
import org.openmrs.module.report.service.ReportService;
import org.openmrs.module.reporting.web.model.IndicatorForm;
import org.openmrs.module.reporting.web.model.IndicatorReportForm;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/module/reporting/reports/indicatorReportEditor")
public class IndicatorReportFormController {

	private Log log = LogFactory.getLog(this.getClass());

	private DateFormat ymd = new SimpleDateFormat("yyyy-MM-dd");
	
    @InitBinder
    public void initBinder(WebDataBinder binder) { 
    	// TODO Switch this to the Context.getDateFormat()
    	//SimpleDateFormat dateFormat = Context.getDateFormat();
    	//SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy"); 
    	//dateFormat.setLenient(false); 
    	//binder.registerCustomEditor(Boolean.class, new CustomBooleanEditor("true", "false", true)); 
    	binder.registerCustomEditor(Date.class, new CustomDateEditor(ymd, true)); 
    }    
    
	
    /**
     * Show the form 
     * 
     * @return
     */
	@RequestMapping(method = RequestMethod.GET)
	public ModelAndView showForm() {
		log.info("Inside show() method");

		return new ModelAndView("/module/reporting/reports/indicatorReportEditor");	
	}	

	
	/**
	 * Add reference data to the request.
	 * 
	 * @param model
	 */
	@ModelAttribute
	public void referenceData(ModelMap model){
		log.info("Building the reference data for all requests");
		
		List<CohortDefinition> cohortDefinitions = 
			Context.getService(CohortDefinitionService.class).getAllCohortDefinitions(false);
		
		List<Indicator> indicators = 
			Context.getService(IndicatorService.class).getAllIndicators(false);
		
		model.addAttribute("cohortDefinitions", cohortDefinitions);
		model.addAttribute("indicators", indicators);
	}	
		
	/**
	 * 
	 * @param request
	 * @param indicatorReport
	 * @param bindingResult
	 * @return
	 */
	@SuppressWarnings("unused")
	@RequestMapping(method = RequestMethod.POST)
	public ModelAndView processForm(
			HttpServletRequest request,
			@RequestParam(value = "id", required=false) String id,
			@RequestParam(value = "value", required=false) String value,
			@ModelAttribute("indicatorReport") IndicatorReportForm indicatorReport, 
			BindingResult bindingResult) {
		log.info("Inside submit() method");
			
		if (bindingResult.hasErrors()) {
			return showForm();
		}
		
		IndicatorReportDefinition reportDefinition = 
			(IndicatorReportDefinition) indicatorReport.getReportDefinition();
		
		// Check whether the report definition is new 
		Boolean isNew = (reportDefinition.getUuid() == null);
		
		String [] selectedIndicatorIds = request.getParameterValues("indicatorId");
		
		log.info("Indicators: " + selectedIndicatorIds);
		
		
		
		// Add indicators to a report schema
		if (selectedIndicatorIds != null && selectedIndicatorIds.length > 0) { 
			
			// Add the indicators to the dataset definition
			for (String uuid : selectedIndicatorIds) { 
				log.info("Looking up indicator: " + uuid);
				// FIXME Assumes cohort indicators
				CohortIndicator cohortIndicator = (CohortIndicator)
					Context.getService(IndicatorService.class).getIndicatorByUuid(uuid);
				
				log.info("Found indicator " + cohortIndicator);					
				if (cohortIndicator != null) { 
					reportDefinition.addCohortIndicator("1.a", "my cohort indicator", cohortIndicator);
					
				}										
			}

			// Save the intermediate dataset definition
			//datasetDefinition = 
			//	(CohortIndicatorDataSetDefinition) Context.getService(DataSetDefinitionService.class).saveDataSetDefinition(datasetDefinition);
		}
		
		log.info("Saving report definition " + reportDefinition.getUuid() + ", name=" + reportDefinition.getName());
		log.info("Dataset definition " + reportDefinition.getDataSetDefinitions().size());
		
		reportDefinition = (IndicatorReportDefinition)
			Context.getService(ReportService.class).saveReportDefinition(reportDefinition);

		
		if (isNew) { 
			return showForm();
		}
		
		return new ModelAndView("redirect:/module/reporting/reports/reportManager.list");
	}
	

	/**
	 * Gets an existing indicator report from the database or creates one
	 * from scratch.
	 * 
	 * @param uuid
	 * @param className
	 * @return
	 */
	@ModelAttribute("indicatorReport")
	public IndicatorReportForm formBackingObject(
			@RequestParam(value = "uuid", required=false) String uuid,
			@RequestParam(value = "className", required=false) String className
	) {
		log.info("Inside formBackingObject(String, String) method with ");
		log.info("UUID=" + uuid + ", className=" + className);
		
		IndicatorReportForm indicatorReport = new IndicatorReportForm();
		
		ReportService reportService = Context.getService(ReportService.class);		

		// Create the indicator and save it to the period indicator report
		IndicatorReportDefinition reportDefinition = (IndicatorReportDefinition) 
			reportService.getReportDefinitionByUuid(uuid);
		

		//if (!PeriodIndicatorReportDefinition.class.isAssignableFrom(reportDefinition.getClass())) { 
		//	throw new APIException("Unsupported report definition type <" + reportDefinition.getClass() + ">");
		//}		
		
		if (reportDefinition == null) { 		
			reportDefinition = new IndicatorReportDefinition();
		} 

		
		indicatorReport.setReportDefinition(reportDefinition);
		indicatorReport.setIndicators(reportDefinition.getIndicators());
		
		
		return indicatorReport;
	}


	/**
	 * Evaluates an indicator report.
	 * 
	 * @param uuid
	 * @param renderType
	 * @param request
	 * @param response
	 * @param model
	 * @throws Exception
	 */
	@RequestMapping("/module/reporting/reports/evaluateIndicatorReport")		
	public void evaluateReport( 
   		@RequestParam(required=false, value="uuid") String uuid,
		@RequestParam(required=false, value="renderAs") String renderType,
		HttpServletRequest request,
		HttpServletResponse response,
		ModelMap model) throws Exception {
		
			
		ReportService reportService = (ReportService) Context.getService(ReportService.class);

		ReportDefinition reportDefinition = reportService.getReportDefinitionByUuid(uuid);				
	
		if (reportDefinition == null)
			throw new APIException("Unable to locate report schema with UUID " + uuid);
	
		EvaluationContext evalContext = new EvaluationContext();
		evalContext.setBaseCohort(Context.getPatientSetService().getAllPatients());
		for (Parameter param : reportDefinition.getParameters() ) { 
			log.info("Setting parameter " + param.getName() + " of class " + param.getClazz() + " = " + request.getParameter(param.getName()) );
			String paramValue = request.getParameter(param.getName());
			// TODO Need to convert from string to object
			// TODO Parameter needs a data type property
			// We don't have enough information at this point
			evalContext.addParameterValue(param.getName(), paramValue);
		}

		// Evaluate the report
		ReportData reportData = reportService.evaluate(reportDefinition, evalContext);
	
		// Render the report
		ReportRenderer renderer = null;
		if ("csv".equalsIgnoreCase(renderType)) { 
			renderer = new CsvReportRenderer();
			response.setContentType("text/csv");
			response.setHeader("Content-Disposition", "attachment; filename=\"" + reportDefinition.getName() + ".csv\"");  
		} 
		else if ("tsv".equalsIgnoreCase(renderType)) { 
			renderer = new TsvReportRenderer();
			response.setContentType("text/csv");
			response.setHeader("Content-Disposition", "attachment; filename=\"" + reportDefinition.getName() + ".tsv\"");  
		} 
		else if ("xls".equalsIgnoreCase(renderType)) { 
			renderer = new TsvReportRenderer();
			response.setContentType("application/vnd.ms-excel");
			response.setHeader("Content-Disposition", "attachment; filename=\"" + reportDefinition.getName() + ".xls\"");  
		} 
		else { 
			throw new APIException("Unknown rendering type");
		}
		renderer.render(reportData, null, response.getOutputStream()); 

		model.addAttribute("reportDefinition", reportDefinition);
	}	
	
	
	
}