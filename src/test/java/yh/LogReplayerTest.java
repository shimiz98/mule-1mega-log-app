package yh;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LogReplayerTest {

	@Test
	void test() {
		LogReplayer.replayZipLog("test1.zip");
	}

}
