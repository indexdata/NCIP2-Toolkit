package org.extensiblecatalog.ncip.v2.aleph;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.extensiblecatalog.ncip.v2.aleph.restdlf.AlephConstants;
import org.extensiblecatalog.ncip.v2.aleph.restdlf.AlephException;
import org.extensiblecatalog.ncip.v2.aleph.restdlf.AlephRemoteServiceManager;
import org.extensiblecatalog.ncip.v2.aleph.restdlf.item.AlephItem;
import org.extensiblecatalog.ncip.v2.aleph.util.AlephUtil;
import org.extensiblecatalog.ncip.v2.aleph.util.ItemToken;
import org.extensiblecatalog.ncip.v2.service.AgencyId;
import org.extensiblecatalog.ncip.v2.service.BibInformation;
import org.extensiblecatalog.ncip.v2.service.BibliographicDescription;
import org.extensiblecatalog.ncip.v2.service.BibliographicId;
import org.extensiblecatalog.ncip.v2.service.BibliographicRecordIdentifierCode;
import org.extensiblecatalog.ncip.v2.service.HoldingsSet;
import org.extensiblecatalog.ncip.v2.service.ItemDescription;
import org.extensiblecatalog.ncip.v2.service.ItemId;
import org.extensiblecatalog.ncip.v2.service.ItemInformation;
import org.extensiblecatalog.ncip.v2.service.ItemOptionalFields;
import org.extensiblecatalog.ncip.v2.service.LookupItemSetInitiationData;
import org.extensiblecatalog.ncip.v2.service.LookupItemSetResponseData;
import org.extensiblecatalog.ncip.v2.service.LookupItemSetService;
import org.extensiblecatalog.ncip.v2.service.Problem;
import org.extensiblecatalog.ncip.v2.service.ProblemType;
import org.extensiblecatalog.ncip.v2.service.RemoteServiceManager;
import org.extensiblecatalog.ncip.v2.service.ServiceContext;
import org.extensiblecatalog.ncip.v2.service.ServiceError;
import org.extensiblecatalog.ncip.v2.service.ServiceException;
import org.extensiblecatalog.ncip.v2.service.ServiceHelper;
import org.extensiblecatalog.ncip.v2.service.Version1BibliographicRecordIdentifierCode;
import org.extensiblecatalog.ncip.v2.service.Version1GeneralProcessingError;
import org.extensiblecatalog.ncip.v2.service.Version1ItemDescriptionLevel;
import org.extensiblecatalog.ncip.v2.service.Version1LookupItemProcessingError;
import org.xml.sax.SAXException;

public class AlephLookupItemSetService implements LookupItemSetService {

	static Logger log = Logger.getLogger(AlephLookupItemSetService.class);

	private static HashMap<String, ItemToken> tokens = new HashMap<String, ItemToken>();

	/**
	 * Construct an AlephRemoteServiceManager; this class is not configurable so there are no parameters.
	 */
	public AlephLookupItemSetService() {
	}

	@Override
	public LookupItemSetResponseData performService(LookupItemSetInitiationData initData, ServiceContext serviceContext, RemoteServiceManager serviceManager)
			throws ServiceException {
		Date sService = new Date();
		LookupItemSetResponseData responseData = new LookupItemSetResponseData();
		AlephRemoteServiceManager alephSvcMgr = (AlephRemoteServiceManager) serviceManager;
		boolean getBibDescription = initData.getBibliographicDescriptionDesired();
		boolean getCircStatus = initData.getCirculationStatusDesired();
		boolean getElectronicResource = initData.getElectronicResourceDesired();
		boolean getHoldQueueLength = initData.getHoldQueueLengthDesired();
		boolean getItemDescription = initData.getItemDescriptionDesired();
		boolean getLocation = initData.getLocationDesired();

		List<BibliographicId> bibIds = initData.getBibliographicIds();

		String token = initData.getNextItemToken();
		ItemToken nextItemToken = null;
		// remove any bib ids from bibIds list that may have already been processed
		if (token != null) {
			nextItemToken = tokens.get(token);
			if (nextItemToken != null) {
				int index = getBibIdIndex(bibIds, nextItemToken.getBibliographicId());
				if (index != -1) {
					// remove the ones already processed
					bibIds.subList(0, index).clear();
				}

				// Remove token from memory hashmap
				tokens.remove(token);
			} else {
				Problem problem = new Problem();
				problem.setProblemDetail("Invalid nextItemToken");
				problem.setProblemValue("nextItemToken =" + token);
				List<Problem> problems = new ArrayList<Problem>();
				problems.add(problem);
				responseData.setProblems(problems);

				return responseData;
			}
			log.debug("after removing already processed Bib ids =" + bibIds);

		}
		List<BibInformation> bibInformations = new ArrayList<BibInformation>();

		if (bibIds != null && bibIds.size() > 0) {

			for (BibliographicId bibId : bibIds) {

				if (bibId.getBibliographicItemId() != null) {
					String id = bibId.getBibliographicItemId().getBibliographicItemIdentifier();
					BibInformation bibInformation = new BibInformation();
					bibInformation.setBibliographicId(bibId);
					try {

						List<AlephItem> alephItems = alephSvcMgr.lookupItem(id, getBibDescription, getCircStatus, getHoldQueueLength, getItemDescription);
						if (alephItems != null) {
							List<ItemInformation> itemInfoList = new ArrayList<ItemInformation>();
							List<HoldingsSet> holdingSets = new ArrayList<HoldingsSet>();

							// TODO: Think about all the returned items with identical ItemId ..
							AlephItem alephItem = alephItems.get(0);
							alephItem.setNumberOfPieces(new BigDecimal(alephItems.size()));

							ItemOptionalFields iof = new ItemOptionalFields();
							HoldingsSet holdingSet = new HoldingsSet();

							if (getBibDescription) {
								AgencyId suppliedAgencyId;
								if (initData.getInitiationHeader() == null || initData.getInitiationHeader().getFromAgencyId() == null)
									suppliedAgencyId = alephSvcMgr.getDefaultAgencyId();
								else
									suppliedAgencyId = initData.getInitiationHeader().getFromAgencyId().getAgencyId();
								BibliographicDescription bDesc = AlephUtil.getBibliographicDescription(alephItem, suppliedAgencyId);
								holdingSet.setBibliographicDescription(bDesc);
							}

							// Schema v2_02 defines to forward itemInfo even though there is no optional information desired
							ItemInformation info = new ItemInformation();
							if (getHoldQueueLength || getCircStatus || getItemDescription) {
								if (getHoldQueueLength)
									iof.setHoldQueueLength(new BigDecimal(alephItem.getHoldQueueLength()));
								if (getCircStatus)
									iof.setCirculationStatus(alephItem.getCirculationStatus());
								if (getItemDescription) {
									ItemDescription itemDescription = new ItemDescription();
									itemDescription.setItemDescriptionLevel(Version1ItemDescriptionLevel.ITEM);
									itemDescription.setCallNumber(alephItem.getCallNumber());
									itemDescription.setCopyNumber(alephItem.getCopyNumber());
									itemDescription.setNumberOfPieces(alephItem.getNumberOfPieces());
									iof.setItemDescription(itemDescription);
								}
								info.setItemOptionalFields(iof);
							}
							itemInfoList.add(info);

							holdingSet.setItemInformations(itemInfoList);
							holdingSets.add(holdingSet);
							bibInformation.setHoldingsSets(holdingSets);

							bibInformations.add(bibInformation);

						} else if (alephSvcMgr.echoParticularProblemsToLUIS) {
							Problem p = new Problem();
							p.setProblemType(Version1LookupItemProcessingError.UNKNOWN_ITEM);
							p.setProblemDetail("Item " + id + " you are searching for doesn't exists.");

							List<Problem> problems = new ArrayList<Problem>();
							problems.add(p);

							bibInformation.setProblems(problems);
							bibInformations.add(bibInformation);
						}
					} catch (IOException ie) {
						Problem p = new Problem();
						p.setProblemType(new ProblemType("Procesing IOException error"));
						p.setProblemDetail(ie.getMessage());
						List<Problem> problems = new ArrayList<Problem>();
						problems.add(p);

						bibInformation.setProblems(problems);
						bibInformations.add(bibInformation);
					} catch (ParserConfigurationException pce) {
						Problem p = new Problem();
						p.setProblemType(new ProblemType("Procesing ParserConfigurationException error"));
						p.setProblemDetail(pce.getMessage());
						List<Problem> problems = new ArrayList<Problem>();
						problems.add(p);

						bibInformation.setProblems(problems);
						bibInformations.add(bibInformation);
					} catch (SAXException se) {
						Problem p = new Problem();
						p.setProblemType(new ProblemType("Procesing SAXException error"));
						p.setProblemDetail(se.getMessage());
						List<Problem> problems = new ArrayList<Problem>();
						problems.add(p);

						bibInformation.setProblems(problems);
						bibInformations.add(bibInformation);
					} catch (AlephException ae) {
						Problem p = new Problem();
						p.setProblemType(new ProblemType("Procesing AlephException error"));
						p.setProblemDetail(ae.getMessage());
						List<Problem> problems = new ArrayList<Problem>();
						problems.add(p);

						bibInformation.setProblems(problems);
						bibInformations.add(bibInformation);
					} catch (Exception e) {
						Problem p = new Problem();
						p.setProblemType(new ProblemType("Unknown procesing exception error"));
						p.setProblemDetail(e.getMessage());
						List<Problem> problems = new ArrayList<Problem>();
						problems.add(p);
						responseData.setProblems(problems);
					}

				} else if (bibId.getBibliographicRecordId() != null) {

					if (bibId.getBibliographicRecordId().getAgencyId() != null) {

						// Execute request if agency Id is blank or NRU
						if (!bibId.getBibliographicRecordId().getAgencyId().getValue().equalsIgnoreCase("")
								&& alephSvcMgr.getAlephAgency(bibId.getBibliographicRecordId().getAgencyId().getValue()) == null) {
							throw new ServiceException(ServiceError.UNSUPPORTED_REQUEST, "This request cannot be processed. Agency ID is invalid or not found.");
						}

					} else {

						BibliographicRecordIdentifierCode code = bibId.getBibliographicRecordId().getBibliographicRecordIdentifierCode();
						if (!code.equals(Version1BibliographicRecordIdentifierCode.OCLC)) {

							BibInformation bibInformation = new BibInformation();
							bibInformation.setProblems(ServiceHelper.generateProblems(Version1GeneralProcessingError.UNAUTHORIZED_COMBINATION_OF_ELEMENT_VALUES_FOR_SYSTEM,
									"//BibliographicRecordId/BibliographicRecordIdentifierCode", code.getScheme() + ": " + code.getValue(), "Bib Id type '" + code.getScheme()
											+ ": " + code.getValue() + "' not supported."));
							bibInformations.add(bibInformation);

						}
					}
					log.debug("Processing Bib id = " + bibId.getBibliographicItemId().getBibliographicItemIdentifier());
					String id = bibId.getBibliographicItemId().getBibliographicItemIdentifier();
					BibInformation bibInformation = new BibInformation();
					bibInformation.setBibliographicId(bibId);
					try {

						List<AlephItem> alephItems = alephSvcMgr.lookupItem(id, getBibDescription, getCircStatus, getHoldQueueLength, getItemDescription);
						if (alephItems != null) {
							List<ItemInformation> itemInfoList = new ArrayList<ItemInformation>();
							List<HoldingsSet> holdingSets = new ArrayList<HoldingsSet>();

							// TODO: Think about all the returned items with identical ItemId ..
							AlephItem alephItem = alephItems.get(0);
							alephItem.setNumberOfPieces(new BigDecimal(alephItems.size()));

							ItemOptionalFields iof = new ItemOptionalFields();
							HoldingsSet holdingSet = new HoldingsSet();

							if (getBibDescription) {
								AgencyId suppliedAgencyId = initData.getInitiationHeader().getFromAgencyId().getAgencyId();
								if (suppliedAgencyId == null)
									suppliedAgencyId = alephSvcMgr.getDefaultAgencyId();
								BibliographicDescription bDesc = AlephUtil.getBibliographicDescription(alephItem, suppliedAgencyId);
								holdingSet.setBibliographicDescription(bDesc);
							}

							// Schema v2_02 defines to forward itemInfo even though there is no optional information desired
							ItemInformation info = new ItemInformation();
							if (getHoldQueueLength || getCircStatus || getItemDescription) {
								if (getHoldQueueLength)
									iof.setHoldQueueLength(new BigDecimal(alephItem.getHoldQueueLength()));
								if (getCircStatus)
									iof.setCirculationStatus(alephItem.getCirculationStatus());
								if (getItemDescription) {
									ItemDescription itemDescription = new ItemDescription();
									itemDescription.setItemDescriptionLevel(Version1ItemDescriptionLevel.ITEM);
									itemDescription.setCallNumber(alephItem.getCallNumber());
									itemDescription.setCopyNumber(alephItem.getCopyNumber());
									itemDescription.setNumberOfPieces(alephItem.getNumberOfPieces());
									iof.setItemDescription(itemDescription);
								}
								info.setItemOptionalFields(iof);
							}
							itemInfoList.add(info);

							holdingSet.setItemInformations(itemInfoList);
							holdingSets.add(holdingSet);
							bibInformation.setHoldingsSets(holdingSets);

							bibInformations.add(bibInformation);

						} else if (alephSvcMgr.echoParticularProblemsToLUIS) {
							Problem p = new Problem();
							p.setProblemType(Version1LookupItemProcessingError.UNKNOWN_ITEM);
							p.setProblemDetail("Item " + id + " you are searching for doesn't exists.");

							List<Problem> problems = new ArrayList<Problem>();
							problems.add(p);

							bibInformation.setProblems(problems);
							bibInformations.add(bibInformation);
						}
					} catch (AlephException e) {
						Problem p = new Problem();
						p.setProblemType(new ProblemType("Processing error"));
						p.setProblemDetail(e.getMessage());
						List<Problem> problems = new ArrayList<Problem>();
						problems.add(p);

						bibInformation.setProblems(problems);
						bibInformations.add(bibInformation);
					} catch (SAXException e) {
						Problem p = new Problem();
						p.setProblemType(new ProblemType("Processing error"));
						p.setProblemDetail(e.getMessage());
						List<Problem> problems = new ArrayList<Problem>();
						problems.add(p);

						bibInformation.setProblems(problems);
						bibInformations.add(bibInformation);
					} catch (ParserConfigurationException e) {
						Problem p = new Problem();
						p.setProblemType(new ProblemType("Processing error"));
						p.setProblemDetail(e.getMessage());
						List<Problem> problems = new ArrayList<Problem>();
						problems.add(p);

						bibInformation.setProblems(problems);
						bibInformations.add(bibInformation);
					} catch (IOException e) {
						Problem p = new Problem();
						p.setProblemType(new ProblemType("Processing error"));
						p.setProblemDetail(e.getMessage());
						List<Problem> problems = new ArrayList<Problem>();
						problems.add(p);

						bibInformation.setProblems(problems);
						bibInformations.add(bibInformation);
					} catch (Exception e) {
						Problem p = new Problem();
						p.setProblemType(new ProblemType("Processing error"));
						p.setProblemDetail(e.getMessage());
						List<Problem> problems = new ArrayList<Problem>();
						problems.add(p);

						responseData.setProblems(problems);
					}
				} else {

					BibInformation bibInformation = new BibInformation();
					bibInformation.setProblems(ServiceHelper.generateProblems(Version1GeneralProcessingError.NEEDED_DATA_MISSING, "BibliographicItemId/RecordId", null,
							"BibliographicItemId/RecordId was not properly set."));
					bibInformations.add(bibInformation);

				}

			}

		} else if (initData.getItemIds() != null && initData.getItemIds().size() > 0) {

			for (ItemId itemId : initData.getItemIds()) {

				ItemInformation itemInformation = new ItemInformation();
				itemInformation.setProblems(ServiceHelper.generateProblems(Version1LookupItemProcessingError.UNKNOWN_ITEM, "//BibliographicRecordId",
						itemId.getItemIdentifierValue(), "Item # '" + itemId.getItemIdentifierValue() + "' not found."));
				List<ItemInformation> itemInformationList = new ArrayList<ItemInformation>();
				itemInformationList.add(itemInformation);
				HoldingsSet holdingsSet = new HoldingsSet();
				holdingsSet.setItemInformations(itemInformationList);
				List<HoldingsSet> holdingsSetList = new ArrayList<HoldingsSet>();
				BibInformation bibInformation = new BibInformation();
				bibInformation.setHoldingsSets(holdingsSetList);
				bibInformations.add(bibInformation);

			}

		} // /else - Do nothing, the fact that the bibInformationsList is empty will cause a Problem to be created below

		if (responseData.getProblems() == null || responseData.getProblem(0) == null)
			responseData.setBibInformations(bibInformations);

		Date eService = new Date();

		log.debug("Service time log : " + (eService.getTime() - sService.getTime()) + "  " + ((eService.getTime() - sService.getTime()) / 1000) + " sec");
		log.debug(responseData);
		log.debug("Tokens in memory: " + tokens);
		return responseData;
	}

	private int getBibIdIndex(List<BibliographicId> bibIds, String bibId) {

		for (int i = 0; i < bibIds.size(); i++) {

			if (bibIds.get(i).getBibliographicItemId().getBibliographicItemIdentifier().equalsIgnoreCase(bibId)) {
				return i;
			}
		}

		return -1;
	}

}
