package pt.ptcris.test.scenarios;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.um.dsi.gavea.orcid.model.work.Work;

import pt.ptcris.test.TestHelper;
import pt.ptcris.test.TestClients;
import pt.ptcris.test.TestClients.Profile;
import pt.ptcris.utils.ORCIDHelper;

/**
 * Features: modification notifications with {same,more}
 * 
 */

public class Scenario6 extends Scenario {

	@Override
	List<Work> setupORCIDFixtureWorks() {
		List<Work> works = new ArrayList<Work>();
		works.add(TestHelper.workDOIEID(null, "0", "0", "0"));
		works.add(TestHelper.workDOIHANDLE(null, "1", "1", "1"));
		return works;
	}

	@Override
	List<Work> setupLocalWorks() {
		List<Work> works = new ArrayList<Work>();
		works.add(TestHelper.workDOIEIDHANDLE(BigInteger.valueOf(2), "0", "0", "0", "1"));
		works.add(TestHelper.workDOI(BigInteger.valueOf(1), "1", "1"));
		return works;
	}

	@Override
	List<Work> expectedImportedLocalWorks() {
		List<Work> works = new ArrayList<Work>();
		works.add(TestHelper.workDOIEIDHANDLE(BigInteger.valueOf(1), null, "0", "0", "1"));
		works.add(TestHelper.workDOI(BigInteger.valueOf(2), null, "1"));
		return works;
	}

	@Override
	ORCIDHelper clientSource() {
		return new ORCIDHelper(TestClients.getPTCRISClient(Profile.TWOVALIDWORKS));
	}

	@Override
	ORCIDHelper clientFixture() {
		return new ORCIDHelper(TestClients.getExternalClient(Profile.TWOVALIDWORKS));
	}

}
