package pt.ptcris;

import java.math.BigInteger;

import org.um.dsi.gavea.orcid.client.exception.OrcidClientException;
import org.um.dsi.gavea.orcid.model.activities.ActivitiesSummary;
import org.um.dsi.gavea.orcid.model.work.Work;

/**
 * Interface that encapsulates the communication with the ORCID client for a
 * specified user profile.
 *
 * Currently focuses on managing {@link org.um.dsi.gavea.orcid.model.work.Work
 * works}.
 */
public interface ORCIDClient {

	/**
	 * Returns the Member API client id that will commit the changes (i.e., the
	 * works' source).
	 *
	 * @return the client id
	 */
	public String getClientId();

	/**
	 * Retrieves a complete work from the ORCID profile (as opposed to only its
	 * summary).
	 *
	 * @param putcode
	 *            the put-code of the work
	 * @return the complete work
	 * @throws OrcidClientException
	 *             if the communication with ORCID fails
	 */
	public Work getWork(BigInteger putcode) throws OrcidClientException;

	/**
	 * Adds a new work to the ORCID profile.
	 *
	 * @param work
	 *            the work to be added to the ORCID profile
	 * @return the put-code assigned by ORCID to the newly created work
	 * @throws OrcidClientException
	 *             if the communication with ORCID fails
	 */
	public BigInteger addWork(Work work) throws OrcidClientException;

	/**
	 * Deletes a work from the ORCID profile.
	 *
	 * @param putcode
	 *            the put-code of the work to be deleted
	 * @throws OrcidClientException
	 *             if the communication with ORCID fails
	 */
	public void deleteWork(BigInteger putcode) throws OrcidClientException;

	/**
	 * Updates a work in the ORCID profile.
	 *
	 * @param putcode
	 *            the put-code of the work to be updated
	 * @param work
	 *            the new state of the work
	 * @throws OrcidClientException
	 *             if the communication with ORCID fails
	 */
	public void updateWork(BigInteger putcode, Work work) throws OrcidClientException;

	/**
	 * Retrieves every activity summary from the ORCID profile.
	 *
	 * @return the activities summary of the ORCID profile
	 * @throws OrcidClientException
	 *             if the communication with ORCID fails
	 */
	public ActivitiesSummary getActivitiesSummary() throws OrcidClientException;

}