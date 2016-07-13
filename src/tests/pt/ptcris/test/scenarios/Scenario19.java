package pt.ptcris.test.scenarios;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.um.dsi.gavea.orcid.client.exception.OrcidClientException;
import org.um.dsi.gavea.orcid.model.work.Work;

import pt.ptcris.ORCIDHelper;

/**
 * Features: export updates with {less,same,more}
 */
public class Scenario19 extends Scenario {

	@Override
	List<Work> setupORCIDWorks() {
		List<Work> works = new ArrayList<Work>();
		works.add(ScenariosHelper.workDOIEIDHANDLE(null, "1", "1", "0", "0"));
		return works;
	}

	@Override
	List<Work> exportLocalWorks() {
		List<Work> works = new ArrayList<Work>();
		works.add(ScenariosHelper.workDOIEIDHANDLE(BigInteger.valueOf(0), "1", "1", "0", "0"));
		return works;
	}

	@Override
	List<Work> expectedSourcedORCIDWorks() {
		List<Work> works = new ArrayList<Work>();
		works.add(ScenariosHelper.workDOIEIDHANDLE(null, "1", "1", "0", "0"));
		return works;
	}

	@Override
	Integer expectedExportCodes(BigInteger code) {
		if (code.equals(BigInteger.valueOf(0)))
			return ORCIDHelper.UPTODATE;
		else
			return ORCIDHelper.ADDOK;
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
