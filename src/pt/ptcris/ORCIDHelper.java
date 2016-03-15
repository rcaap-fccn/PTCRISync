package pt.ptcris;

import static org.orcid.core.api.OrcidApiConstants.ACTIVITIES;
import static org.orcid.core.api.OrcidApiConstants.PUTCODE;
import static org.orcid.core.api.OrcidApiConstants.VND_ORCID_XML;
import static org.orcid.core.api.OrcidApiConstants.WORK;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.orcid.api.common.OrcidClientHelper;
import org.orcid.jaxb.model.error_rc2.OrcidError;
import org.orcid.jaxb.model.record.summary_rc2.ActivitiesSummary;
import org.orcid.jaxb.model.record.summary_rc2.WorkGroup;
import org.orcid.jaxb.model.record.summary_rc2.WorkSummary;
import org.orcid.jaxb.model.record_rc2.ExternalID;
import org.orcid.jaxb.model.record_rc2.ExternalIDs;
import org.orcid.jaxb.model.record_rc2.Relationship;
import org.orcid.jaxb.model.record_rc2.Work;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;

public class ORCIDHelper {


	private final OrcidClientHelper rest;
	private final String profile;
	private final String accessToken;


	public ORCIDHelper(String baseUri, String profile, String accessToken) throws URISyntaxException {
		this.profile = profile;
		this.accessToken = accessToken;
		//        rest = new RESTHelper(baseUri);
		rest = new OrcidClientHelper(new URI(baseUri),Client.create());
	}

	/**
	 * Retrieves a full work from the ORCID {@link #profile}.
	 * 
	 * @param putCode The put-code of the work.
	 * @return The full work.
	 * @throws ORCIDException 
	 */
	public Work getFullWork(Long putCode) throws ORCIDException {
		URI uri = UriBuilder.fromPath(WORK + PUTCODE).build(profile, putCode);
	
		ClientResponse r = rest.getClientResponseWithToken(uri, VND_ORCID_XML, accessToken);
	
		if (r.getStatus() != Response.Status.OK.getStatusCode()) {
			OrcidError err = r.getEntity(OrcidError.class);
			throw new ORCIDException(err);
		}
	
		Work work = r.getEntity(Work.class);
		return work;
	}

	/**
	 * Add a work to the ORCID {@link #profile}.
	 * 
	 * @param work
	 *            The work to be added to the ORCID {@link #profile}
	 * @return the put-code in the ORCID {@link #profile} of the newly created work.            
	 * @throws ORCIDException 
	 */
	public Long addWork(Work work) throws ORCIDException {
		URI uri = UriBuilder.fromPath(WORK).build(profile);
		ClientResponse r = rest.postClientResponseWithToken(uri, VND_ORCID_XML, work, accessToken);
	
		if (r.getStatus() != Response.Status.CREATED.getStatusCode()) {
			OrcidError err = r.getEntity(OrcidError.class);
			throw new ORCIDException(err);
		}
	
		String r_uri = r.getLocation().getPath();
		String r_putcode = r_uri.substring(r_uri.lastIndexOf("/")+1);
		return Long.valueOf(r_putcode);
	}

	/**
	 * Delete a work from the ORCID {@link #profile}.
	 * 
	 * @param putCode
	 *            The put-code of the work to be deleted.
	 * @throws ORCIDException 
	 */
	public void deleteWork(Long putCode) throws ORCIDException {
		URI uri = UriBuilder.fromPath(WORK + PUTCODE).build(profile, putCode);
		ClientResponse r = rest.deleteClientResponseWithToken(uri, VND_ORCID_XML, accessToken);
	
		if (r.getStatus() != Response.Status.NO_CONTENT.getStatusCode()) {
			OrcidError err = r.getEntity(OrcidError.class);
			throw new ORCIDException(err);
		}
	
		// NOTE: according to the ORCID API, to delete a work, one must provide the entire list of works in the ORCID profile minus the work(s) that
		// should be deleted. This means that this operation must be done in three steps: first, retrieve the entire set of works; second, remove the
		// work to be deleted from the list of works; and three, send the updated list to the ORCID API.
	}

	/**
	 * Update a work in the ORCID {@link #profile}.
	 * 
	 * @param updateRecord
	 *            The updateRecord that contains both the local and remote Works (the remote work is updated based on the data in the local work)
	 * @return the updated work as represented in the ORCID {@link #profile}..            
	 * @throws ORCIDException 
	 */
	public Work updateWork(Long putCode, Work work) throws ORCIDException {
		URI uri = UriBuilder.fromPath(WORK + PUTCODE).build(profile, putCode);
		work.setPutCode(putCode);
		ClientResponse r = rest.putClientResponseWithToken(uri, VND_ORCID_XML, work, accessToken);
	
		if (r.getStatus() != Response.Status.OK.getStatusCode()) {
			OrcidError err = r.getEntity(OrcidError.class);
			throw new ORCIDException(err);
		}
	
		Work w = r.getEntity(Work.class);
		return w;
	
		// NOTE: according to the ORCID API, to update a work, one must provide the entire list of works in the ORCID profile including the work(s)
		// that should be updated. This means that this operation must be done in three steps: first, retrieve the entire set of works; second,
		// replace the work to be updated with the new record in the list of works; and three, send the updated list to the ORCID API.
	}

	/**
	 * Retrieves the entire set of work summaries in the ORCID profile.
	 * Merges each ORCID group into a single summary, following {@link #groupToWork}.
	 * 
	 * @return The set of work summaries in the ORCID profile
	 * @throws ORCIDException 
	 */
	public List<WorkSummary> getAllWorkSummaries() throws ORCIDException {
		ActivitiesSummary summs = getActivitiesSummary();
		Stream<WorkGroup> groups = summs.getWorks().getWorkGroup().stream();
		Stream<WorkSummary> works = groups.map(w -> groupToWork(w));
		return works.collect(Collectors.toList());
	}

	/**
	 * Retrieves the entire set of works in the ORCID profile whose source is the local CRIS service.
	 * 
	 * @param sourceName
	 *            The source name of the local CRIS service.
	 * @return The set of work summaries in the ORCID profile whose source is useDefault.
	 * @throws ORCIDException 
	 */
	public List<WorkSummary> getSourcedWorkSummaries(String sourceName) throws ORCIDException {
		ActivitiesSummary summs = getActivitiesSummary();
		Stream<WorkGroup> groups = summs.getWorks().getWorkGroup().stream();
		Stream<WorkSummary> work_summs = groups.map(WorkGroup::getWorkSummary)
											   .flatMap(List::stream)
											   .filter(s -> s.getSource().getSourceName().getContent().equals(sourceName));
		return work_summs.collect(Collectors.toList());
	}

	/**
	 * Retrieves every activity summary of the ORCID profile.
	 * 
	 * @return The activities summary of the ORCID profile.
	 * @throws ORCIDException 
	 */
	private ActivitiesSummary getActivitiesSummary() throws ORCIDException {
		URI uri = UriBuilder.fromPath(ACTIVITIES).build(profile);
		ClientResponse r = rest.getClientResponseWithToken(uri, VND_ORCID_XML, accessToken);

		if (r.getStatus() != Response.Status.OK.getStatusCode()) {
			OrcidError err = r.getEntity(OrcidError.class);
			throw new ORCIDException(err);
		}

		ActivitiesSummary acts = r.getEntity(ActivitiesSummary.class);
		return acts;
	}

	/**
	 * Retrieves the set of productions (from works) that share some UIDs with a work summary.
	 * 
	 * @param summary
	 *            The work summary to compare with the list of works.
	 * @param works
	 *            The set of works to search for productions with shared UIDs.
	 * @return The set of works with matching UIDs.
	 */
	public static List<Work> getWorksWithSharedUIDs(WorkSummary summary, List<Work> works) {
		List<Work> matches = new LinkedList<Work>();
		for (Work match : works) {
			if (checkDuplicateUIDs(match.getExternalIdentifiers(), summary.getExternalIdentifiers()))
				matches.add(match);
		}
		return matches;
	}

	/**
	 * Tests whether two sets of external IDs have duplicates.
	 * The algorithm is the same as the one implemented by ORCID.
	 * Only considered duplicate if UIDs have the same relationship and are not "part of".
	 * @param uids1
	 * @param uids2
	 * @return
	 */
	private static boolean checkDuplicateUIDs(ExternalIDs uids1, ExternalIDs uids2) {
		if (uids2 != null && uids1 != null) {
			for (ExternalID uid2 : uids2.getExternalIdentifier()) {
				for (ExternalID uid1 : uids1.getExternalIdentifier()) {
					if (sameButNotBothPartOf(uid2.getRelationship(), uid1.getRelationship())
							&& uid1.equals(uid2)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Tests whether two UIDs relationships are the same but not part of.
	 * @param r1
	 * @param r2
	 * @return
	 */
	private static boolean sameButNotBothPartOf(Relationship r1, Relationship r2){
		if (r1 == null && r2 == null)
			return true;
		if (r1 != null && r1.equals(r2) && !r1.equals(Relationship.PART_OF))
			return true;
		return false;
	}


	/**
	 * Merges a group into a work.
	 * Simply selects the first of the group and assigns it any extra UIDs.
	 * @param group The group to be merged.
	 * @return The resulting work summary.
	 */
	public static WorkSummary groupToWork(WorkGroup group) {
		WorkSummary aux = group.getWorkSummary().get(0);
		WorkSummary dummy = new WorkSummary();
		dummy.setCreatedDate(aux.getCreatedDate());
		dummy.setDisplayIndex(aux.getDisplayIndex());
		dummy.setExternalIdentifiers(aux.getExternalIdentifiers());
		dummy.setLastModifiedDate(aux.getLastModifiedDate());
		dummy.setPath(aux.getPath());
		dummy.setPublicationDate(aux.getPublicationDate());
		dummy.setPutCode(aux.getPutCode());
		dummy.setSource(aux.getSource());
		dummy.setTitle(aux.getTitle());
		dummy.setType(aux.getType());
		dummy.setVisibility(aux.getVisibility());
		// TODO: add the other UIDs of the group
		return dummy;
	}


	/**
	 * Checks if localWork is already up to date on the information from remoteWork, i.e., localWork already has the same UIDs as remoteWork
	 * 
	 * @param localWork
	 *            The local work to check if it is up to date
	 * @param remoteWork
	 *            The remote work to use when checking if the local work is up to date
	 * @return true if all the UIDs between the two works are the same, false otherwise
	 */
	public static boolean isAlreadyUpToDate(Work localWork, Work remoteWork) {
		// TODO Compare the two records to check if they are equal (when it comes to matching UIDs)
		return false;
	}


}