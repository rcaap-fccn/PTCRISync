package pt.ptcris;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.um.dsi.gavea.orcid.client.exception.OrcidClientException;
import org.um.dsi.gavea.orcid.model.activities.ActivitiesSummary;
import org.um.dsi.gavea.orcid.model.activities.ActivitiesSummary.Works;
import org.um.dsi.gavea.orcid.model.activities.Identifier;
import org.um.dsi.gavea.orcid.model.activities.WorkGroup;
import org.um.dsi.gavea.orcid.model.common.ActivitySummary;
import org.um.dsi.gavea.orcid.model.common.ClientId;
import org.um.dsi.gavea.orcid.model.common.RelationshipType;
import org.um.dsi.gavea.orcid.model.work.ExternalIdentifier;
import org.um.dsi.gavea.orcid.model.work.ExternalIdentifierType;
import org.um.dsi.gavea.orcid.model.work.Work;
import org.um.dsi.gavea.orcid.model.work.WorkExternalIdentifiers;
import org.um.dsi.gavea.orcid.model.work.WorkSummary;

import pt.ptcris.exceptions.InvalidWorkException;
import pt.ptcris.workers.ORCIDGetWorker;

/**
 * An helper to simplify the use of the low-level ORCID
 * {@link pt.ptcris.ORCIDClient client}.
 *
 */
public class ORCIDHelper {

	public static final int CLIENTERROR = 500;
	public static final int UPTODATE = 304;
	public static final int UPDATEOK = 200;
	public static final int ADDOK = 200;
	public static final int INVALID = -11;
	public static final int CONFLICT = 409;

	public static final String INVALID_EXTERNALIDENTIFIERS = "ExternalIdentifiers";
	public static final String INVALID_WORKEXTERNALIDENTIFIERS = "WorkExternalIdentifiers";
	public static final String INVALID_TITLE = "Title";
	public static final String INVALID_PUBLICATIONDATE = "PublicationDate";
	public static final String INVALID_YEAR = "Year";
	public static final String INVALID_TYPE = "Type";
	
	/**
	 * Whether to multi-thread the "get" of full works.
	 */
	private boolean threaded = true;

	private static final Logger _log = LogManager.getLogger(ORCIDHelper.class);

	/**
	 * The client used to communicate with ORCID. Defines the ORCID user profile
	 * being managed and the Member API id being user to source works.
	 */
	public final ORCIDClient client;

	private ExecutorService executor = Executors.newFixedThreadPool(10);

	/**
	 * Initializes the helper with a given ORCID client.
	 * 
	 * @param orcidClient
	 *            The ORCID client.
	 * @throws OrcidClientException
	 *             If the communication with ORCID fails.
	 */
	public ORCIDHelper(ORCIDClient orcidClient) {
		this.client = orcidClient;
	}

	/**
	 * Retrieves the entire set of work summaries from the ORCID profile that
	 * have at least an external identifier set. Merges each ORCID group into a
	 * single summary, following {@link #groupToWork}.
	 * 
	 * @return The set of work summaries in the ORCID profile.
	 * @throws OrcidClientException
	 *             If the communication with ORCID fails.
	 * @throws NullPointerException
	 */
	public List<WorkSummary> getAllWorkSummaries() throws OrcidClientException {
		ActivitiesSummary activitiesSummary = client.getActivitiesSummary();
		List<WorkSummary> workSummaryList = new LinkedList<WorkSummary>();
		if (activitiesSummary != null && activitiesSummary.getWorks() != null) {
			List<WorkGroup> workGroupList = activitiesSummary.getWorks().getGroup();
			for (WorkGroup group : workGroupList)
				workSummaryList.add(groupToWork(group));
		}
		return workSummaryList;
	}

	/**
	 * Retrieves the entire set of work summaries in the ORCID profile whose
	 * source is the Member API id defined in the ORCID client.
	 * 
	 * @return The set of work summaries in the ORCID profile for the defined
	 *         source.
	 * @throws OrcidClientException
	 *             If the communication with ORCID fails.
	 * @throws NullPointerException
	 */
	public List<WorkSummary> getSourcedWorkSummaries() throws OrcidClientException, NullPointerException {
		ActivitiesSummary activitiesSummary = client.getActivitiesSummary();
		String sourceClientID = client.getClientId();
		List<WorkSummary> workSummaryList = new LinkedList<WorkSummary>();

		if (activitiesSummary != null && activitiesSummary.getWorks() != null) {
			Works works = activitiesSummary.getWorks();
			List<WorkGroup> workGroupList = works.getGroup();
			for (WorkGroup workGroup : workGroupList) {
				for (WorkSummary workSummary : workGroup.getWorkSummary()) {
					ClientId workClient = workSummary.getSource().getSourceClientId();
					// may be null is entry added by the user
					if (workClient != null && workClient.getUriPath().equals(sourceClientID)) {
						workSummaryList.add(workSummary);
					}
				}
			}
		}
		return workSummaryList;
	}

	/**
	 * Deletes the entire set of work summaries in the ORCID profile whose
	 * source is the Member API id defined in the ORCID client.
	 * 
	 * @throws OrcidClientException
	 *             If the communication with ORCID fails.
	 */
	public void deleteAllSourcedWorks() throws OrcidClientException {
		List<WorkSummary> workSummaryList = this.getSourcedWorkSummaries();

		for (WorkSummary workSummary : workSummaryList) {
			client.deleteWork(workSummary.getPutCode());
		}
	}

	/**
	 * @see {@link ORCIDClient#deleteWork(BigInteger)}
	 */
	public void deleteWork(BigInteger putCode) throws OrcidClientException {
		_log.debug("[deleteWork] " + putCode);

		client.deleteWork(putCode);
	}

	/**
	 * TODO: doc
	 * 
	 * @see {@link ORCIDClient#getWork(BigInteger)}
	 */
	public void getFullWork(WorkSummary work, Map<BigInteger,Work> works) throws OrcidClientException {
		_log.debug("[getFullWork] " + work.getPutCode());
		if (threaded) {
			ORCIDGetWorker worker = new ORCIDGetWorker(client, works, work, _log);
			executor.execute(worker);
		} else {
			Work fullWork = client.getWork(work.getPutCode());
			fullWork.setExternalIdentifiers(work.getExternalIdentifiers());
			cleanWorkLocalKey(fullWork);
			works.put(work.getPutCode(),fullWork);
		}
	}

	/**
	 * @see {@link ORCIDClient#getWork(BigInteger)}
	 */
	public Work getFullWork(BigInteger putCode) throws OrcidClientException {
		_log.debug("[getFullWork] " + putCode);
		return client.getWork(putCode);
	}

	/**
	 * @see {@link ORCIDClient#updateWork(BigInteger, Work)}
	 */
	public void updateWork(BigInteger putCode, Work work) throws OrcidClientException {
		_log.debug("[updateWork] " + putCode);

		Work clone = ORCIDHelper.clone(work);
		// Remove any putCode if exists
		clone.setPutCode(putCode);

		client.updateWork(putCode, clone);
	}

	/**
	 * @see {@link ORCIDClient#addWork(Work)}
	 */
	public BigInteger addWork(Work work) throws OrcidClientException {
		_log.debug("[addWork]" + getWorkTitle(work));

		Work clone = ORCIDHelper.clone(work);
		// Remove any putCode if exists
		clone.setPutCode(null);

		BigInteger putCode = client.addWork(clone);
		_log.debug("[addWork] " + putCode);

		return putCode;
	}

	/**
	 * @see {@link ORCIDClient#getActivitiesSummary()}
	 */
	public ActivitiesSummary getActivitiesSummary() throws OrcidClientException {
		_log.debug("[getActivitiesSummary]");
		return client.getActivitiesSummary();
	}

	/**
	 * Waits for all active works communicating with ORCID to finish (if
	 * multi-threading is enabled).
	 * 
	 * @return Whether the workers finished before the timeout.
	 * @throws InterruptedException
	 */
	public boolean waitWorkers() throws InterruptedException {
		if (!threaded)
			return true;
		executor.shutdown();
		boolean timeout = executor.awaitTermination(100, TimeUnit.SECONDS);
		executor = Executors.newFixedThreadPool(100);
		return timeout;
	}

	/**
	 * Retrieves the entire set of putCodes from an Activities Summary,
	 * independently of the source.
	 * 
	 * @param activitiesSummary
	 *            the summaries from which to collect the put-codes.
	 * @return a list of put-codes in the summaries.
	 */
	public static List<BigInteger> getSummaryPutCodes(ActivitiesSummary activitiesSummary) throws NullPointerException {
		List<BigInteger> pubCodesList = new LinkedList<BigInteger>();
		List<WorkSummary> workSummaryList;
		BigInteger putCode;

		for (WorkGroup workGroup : activitiesSummary.getWorks().getGroup()) {
			workSummaryList = workGroup.getWorkSummary();
			for (WorkSummary workSummary : workSummaryList) {
				putCode = workSummary.getPutCode();
				pubCodesList.add(putCode);
			}
			// putCode = workGroup.getWorkSummary().get(0).getPutCode();
			// pubCodesList.add(putCode);
		}

		return pubCodesList;
	}

	/**
	 * Retrieves the title from a work.
	 * 
	 * @param work
	 *            the work.
	 * @return the work's title.
	 * @throws NullPointerException
	 */
	public static String getWorkTitle(Work work) throws NullPointerException {
		return work.getTitle().getTitle();
	}

	public static String getWorkTitle(WorkSummary work) throws NullPointerException {
		return work.getTitle().getTitle();
	}

	/**
	 * TODO: doc
	 * 
	 * @param work
	 * @return
	 */
	public static BigInteger getWorkLocalKey(ActivitySummary work) {
		return work.getPutCode();
	}

	/**
	 * TODO: doc
	 * 
	 * @param work
	 * @return
	 */
	public static void setWorkLocalKey(ActivitySummary work, BigInteger putcode) {
		work.setPutCode(putcode);
	}

	/**
	 * TODO: doc
	 * 
	 * @param work
	 * @return
	 */
	public static void cleanWorkLocalKey(ActivitySummary work) {
		work.setPutCode(null);
	}

	/**
	 * Calculates the symmetric difference of {@link ExternalIdentifier external
	 * identifiers} between a work and a set of works. Works that do not match
	 * (i.e., no identifiers is common) are ignored.
	 * 
	 * @param work
	 *            The work summary to be compared with <code>works</code>.
	 * @param works
	 *            The set of works to be compared with <code>work</code>.
	 * @return The symmetric difference of external identifiers between
	 *         <code>work</code> and each <code>works</code>.
	 */
	public static Map<Work, ExternalIdentifiersUpdate> getExternalIdentifiersDiff(WorkSummary work,
			Collection<Work> works) {
		Map<Work, ExternalIdentifiersUpdate> matches = new HashMap<Work, ExternalIdentifiersUpdate>();
		for (Work match : works) {
			ExternalIdentifiersUpdate aux = new ExternalIdentifiersUpdate(match.getExternalIdentifiers(),
					work.getExternalIdentifiers());
			if (!aux.same.isEmpty())
				matches.put(match, aux);
		}
		return matches;
	}

	/**
	 * Checks whether a work is already up to date regarding another one, i.e.,
	 * whether a work has the same UIDs as another one.
	 * 
	 * This test is expected to be used by the import algorithms, where only new
	 * UIDs are to be considered.
	 * 
	 * @param existingWork
	 *            The potentially out of date work.
	 * @param workSummary
	 *            The up to date work.
	 * @return true if all the UIDs between the two works are the same, false
	 *         otherwise.
	 */
	public static boolean hasNewIDs(Work existingWork, WorkSummary workSummary) {
		ExternalIdentifiersUpdate aux = new ExternalIdentifiersUpdate(existingWork.getExternalIdentifiers(),
				workSummary.getExternalIdentifiers());

		return aux.more.isEmpty();
	}

	/**
	 * Checks whether a work is already up to date regarding another one,
	 * considering the UIDs and the meta-data.
	 * 
	 * This test is expected to be used by the export algorithms, where the
	 * meta-data is expected to be up-to-date on the remote profile.
	 * 
	 * @param existingWork
	 *            The potentially out of date work.
	 * @param workSummary
	 *            The up to date work.
	 * @return true if all the UIDs and the meta-data between the two works are
	 *         the same, false otherwise.
	 */
	public static boolean isUpToDate(Work existingWork, WorkSummary workSummary) {
		return isIDsUpToDate(existingWork, workSummary) && isMetaUpToDate(existingWork, workSummary);
	}

	private static boolean isMetaUpToDate(Work work1, WorkSummary work2) {
		boolean res = true;
		res &= (work1.getTitle() == null && work2.getTitle() == null)
				|| (work1.getTitle() != null && work2.getTitle() != null && getWorkTitle(work1).equals(
						getWorkTitle(work2)));
		res &= (work1.getPublicationDate() == null && work2.getPublicationDate() == null)
				|| (work1.getPublicationDate() != null && work2.getPublicationDate() != null && work1
						.getPublicationDate().getYear().getValue()
						.equals(work2.getPublicationDate().getYear().getValue()));
		res &= (work1.getType() == null && work2.getType() == null)
				|| (work1.getType() != null && work2.getType() != null && work1.getType().equals(work2.getType()));
		// TODO: contributors! but they are not in the summary...
		return res;
	}

	private static boolean isIDsUpToDate(Work existingWork, Work workSummary) {
		ExternalIdentifiersUpdate aux = new ExternalIdentifiersUpdate(existingWork.getExternalIdentifiers(),
				workSummary.getExternalIdentifiers());
		return aux.more.isEmpty() && aux.less.isEmpty();
	}

	public static boolean isUpToDate(Work existingWork, Work workSummary) {
		return isIDsUpToDate(existingWork, workSummary) && isMetaUpToDate(existingWork, workSummary);
	}

	private static boolean isMetaUpToDate(Work work1, Work work2) {
		boolean res = true;
		res &= (work1.getTitle() == null && work2.getTitle() == null)
				|| (work1.getTitle() != null && work2.getTitle() != null && getWorkTitle(work1).equals(
						getWorkTitle(work2)));
		res &= (work1.getPublicationDate() == null && work2.getPublicationDate() == null)
				|| (work1.getPublicationDate() != null && work2.getPublicationDate() != null && work1
						.getPublicationDate().getYear().getValue()
						.equals(work2.getPublicationDate().getYear().getValue()));
		res &= (work1.getType() == null && work2.getType() == null)
				|| (work1.getType() != null && work2.getType() != null && work1.getType().equals(work2.getType()));
		// TODO: contributors! but they are not in the summary...
		return res;
	}

	private static boolean isIDsUpToDate(Work existingWork, WorkSummary workSummary) {
		ExternalIdentifiersUpdate aux = new ExternalIdentifiersUpdate(existingWork.getExternalIdentifiers(),
				workSummary.getExternalIdentifiers());
		return aux.more.isEmpty() && aux.less.isEmpty();
	}

	public static Set<String> testMinimalQuality(Work work) throws InvalidWorkException {
		Set<String> res = new HashSet<String>();
		if (work.getExternalIdentifiers() == null)
			res.add(INVALID_EXTERNALIDENTIFIERS);
		else if (work.getExternalIdentifiers().getWorkExternalIdentifier() == null
				|| work.getExternalIdentifiers().getWorkExternalIdentifier().isEmpty())
			res.add(INVALID_WORKEXTERNALIDENTIFIERS);
		if (work.getTitle() == null)
			res.add(INVALID_TITLE);
		else if (work.getTitle().getTitle() == null)
			res.add(INVALID_TITLE);
		if (work.getPublicationDate() == null)
			res.add(INVALID_PUBLICATIONDATE);
		else if (work.getPublicationDate().getYear() == null)
			res.add(INVALID_YEAR);
		if (work.getType() == null)
			res.add(INVALID_TYPE);
		// TODO: contributors! but they are not in the summary...
		if (!res.isEmpty()) {
			throw new InvalidWorkException(res);
		}
		return res;
	}

	public static Set<String> testMinimalQuality(WorkSummary work) {
		Set<String> res = new HashSet<String>();
		if (work.getExternalIdentifiers() == null)
			res.add(INVALID_EXTERNALIDENTIFIERS);
		else if (work.getExternalIdentifiers().getWorkExternalIdentifier() == null
				|| work.getExternalIdentifiers().getWorkExternalIdentifier().isEmpty())
			res.add(INVALID_WORKEXTERNALIDENTIFIERS);
		if (work.getTitle() == null)
			res.add(INVALID_TITLE);
		else if (work.getTitle().getTitle() == null)
			res.add(INVALID_TITLE);
		if (work.getPublicationDate() == null)
			res.add(INVALID_PUBLICATIONDATE);
		else if (work.getPublicationDate().getYear() == null)
			res.add(INVALID_YEAR);
		if (work.getType() == null)
			res.add(INVALID_TYPE);
		// TODO: contributors! but they are not in the summary...
		return res;
	}

	// TODO: needed because JAXB does not define equals
	public static boolean equalsUIDs(Set<ExternalIdentifier> uids1, Set<ExternalIdentifier> uids2) {
		if (uids1.size() != uids2.size())
			return false;
		for (ExternalIdentifier x : uids1) {
			boolean found = false;
			Iterator<ExternalIdentifier> it = uids2.iterator();
			while (it.hasNext() && !found) {
				ExternalIdentifier y = it.next();
				if (x.getExternalIdentifierId().equals(y.getExternalIdentifierId())
						&& x.getExternalIdentifierType().equals(y.getExternalIdentifierType())
						&& x.getRelationship().equals(y.getRelationship()))
					found = true;
			}
			if (!found)
				return false;
		}
		return true;
	}

	/**
	 * Merges a work group into a single work summary. Simply selects the
	 * meta-data from the first work of the group (i.e., the preferred one) and
	 * assigns it any extra external identifiers from the remainder works.
	 * 
	 * @param group
	 *            The work group to be merged.
	 * @return The resulting work summary.
	 */
	private static WorkSummary groupToWork(WorkGroup group) {
		WorkSummary aux = group.getWorkSummary().get(0);
		WorkSummary dummy = clone(aux);

		List<ExternalIdentifier> eids = new ArrayList<ExternalIdentifier>();
		for (Identifier id : group.getIdentifiers().getIdentifier()) {
			ExternalIdentifier eid = new ExternalIdentifier();
			eid.setRelationship(RelationshipType.SELF);
			eid.setExternalIdentifierType(ExternalIdentifierType
					.fromValue(id.getExternalIdentifierType().toLowerCase()));
			eid.setExternalIdentifierId(id.getExternalIdentifierId());
			eids.add(eid);
		}
		dummy.setExternalIdentifiers(new WorkExternalIdentifiers(eids));

		return dummy;
	}

	public static void copy(ActivitySummary from, ActivitySummary to) {
		to.setCreatedDate(from.getCreatedDate());
		to.setDisplayIndex(from.getDisplayIndex());
		to.setLastModifiedDate(from.getLastModifiedDate());
		to.setPath(from.getPath());
		to.setPutCode(from.getPutCode());
		to.setSource(from.getSource());
		to.setVisibility(from.getVisibility());
	}

	public static WorkSummary clone(WorkSummary aux) {
		WorkSummary dummy = new WorkSummary();
		copy(aux, dummy);
		dummy.setPublicationDate(aux.getPublicationDate());
		dummy.setTitle(aux.getTitle());
		dummy.setType(aux.getType());
		dummy.setExternalIdentifiers(aux.getExternalIdentifiers());
		return dummy;
	}

	public static Work clone(Work aux) {
		Work dummy = new Work();
		copy(aux, dummy);
		dummy.setPublicationDate(aux.getPublicationDate());
		dummy.setTitle(aux.getTitle());
		dummy.setType(aux.getType());
		dummy.setExternalIdentifiers(aux.getExternalIdentifiers());
		dummy.setContributors(aux.getContributors());

		dummy.setCitation(aux.getCitation());
		dummy.setContributors(aux.getContributors());
		dummy.setCountry(aux.getCountry());
		dummy.setJournalTitle(aux.getJournalTitle());
		dummy.setLanguageCode(aux.getLanguageCode());
		dummy.setShortDescription(aux.getShortDescription());
		dummy.setUrl(aux.getUrl());
		return dummy;
	}

}
