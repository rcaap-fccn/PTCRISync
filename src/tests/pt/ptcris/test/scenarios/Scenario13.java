package pt.ptcris.test.scenarios;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.um.dsi.gavea.orcid.client.exception.OrcidClientException;
import org.um.dsi.gavea.orcid.model.work.Work;

import pt.ptcris.ORCIDHelper;

/**
 * Features: export del export update {same,more} modification notifications
 * with {same,more}
 * 
 */
public class Scenario13 extends Scenario {

	@Override
	List<Work> setupORCIDWorks() {
		List<Work> works = new ArrayList<Work>();
		works.add(ScenariosHelper.workDOI(null, "3", "0"));
		works.add(ScenariosHelper.workHANDLE(null, "1", "1"));
		return works;
	}

	@Override
	List<Work> setupLocalWorks() {
		List<Work> works = new ArrayList<Work>();
		works.add(ScenariosHelper.workHANDLE(BigInteger.valueOf(1), "1", "1"));
		return works;
	}

	@Override
	List<Work> exportLocalWorks() {
		List<Work> works = new ArrayList<Work>();
		works.add(ScenariosHelper.workDOIHANDLE(BigInteger.valueOf(2), "3", "0", "1"));
		return works;
	}

	@Override
	List<Work> expectedSourcedORCIDWorks() {
		List<Work> works = new ArrayList<Work>();
		works.add(ScenariosHelper.workDOIHANDLE(BigInteger.valueOf(2), "3", "0", "1"));
		return works;
	}

	@Override
	List<Work> expectedImportedLocalWorks() {
		List<Work> works = new ArrayList<Work>();
		works.add(ScenariosHelper.workDOI(BigInteger.valueOf(1), null, "0"));
		return works;
	}
	
	@Override
	List<Work> expectedImportedInvalidWorks() {
		List<Work> works = new ArrayList<Work>();
		Work work = ScenariosHelper.workDOI(null, "1", "5");
		work.setPublicationDate(null);
		works.add(work);
		return works;
	}

	@Override
	ORCIDHelper clientSource() throws OrcidClientException {
		return new ORCIDHelper(ScenarioOrcidClient.getClientWork(0));
	}

	@Override
	ORCIDHelper clientFixture() throws OrcidClientException {
		return new ORCIDHelper(ScenarioOrcidClient.getClientWorkFixture(0));
	}

}
