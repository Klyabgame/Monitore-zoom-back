package com.ZoomAccessDashboard.zoomaccess;

import com.ZoomAccessDashboard.zoomaccess.infrastructure.input.rest.dto.ZoomPayloadDto;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class ZoomaccessApplicationTests {

	@Test
	void contextLoads() {
	}

	@org.springframework.beans.factory.annotation.Autowired
	private ObjectMapper springObjectMapper;

	@Test
	void testDeserializationOfZoomPayload() throws Exception {
		String json = """
				{
				  "event": "meeting.participant_joined",
				  "event_ts": 1779485366,
				  "payload": {
				    "account_id": "acc123",
				    "plainToken": "token123",
				    "object": {
				      "id": "meeting123",
				      "uuid": "uuid123",
				      "host_id": "host123",
				      "topic": "topic123",
				      "start_time": "time123",
				      "timezone": "UTC",
				      "participant": {
				        "user_id": "usr123",
				        "user_name": "name123",
				        "email": "email@email.com",
				        "id": "id123",
				        "registrant_id": "reg123",
				        "date_time": "2026-05-22T16:29:26Z",
				        "public_ip": "1.2.3.4"
				      }
				    }
				  }
				}
				""";

		ObjectMapper mapper = new ObjectMapper()
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		ZoomPayloadDto dto = mapper.readValue(json, ZoomPayloadDto.class);
		assertNotNull(dto);
		assertNotNull(dto.payload());
		assertNotNull(dto.payload().object());
		assertNotNull(dto.payload().object().participant());
	}

	@Test
	void testSpringObjectMapperDeserialization() throws Exception {
		String json = """
				{
				  "event": "meeting.participant_joined",
				  "event_ts": 1779485366,
				  "payload": {
				    "account_id": "acc123",
				    "plainToken": "token123",
				    "object": {
				      "id": "meeting123",
				      "uuid": "uuid123",
				      "host_id": "host123",
				      "topic": "topic123",
				      "start_time": "time123",
				      "timezone": "UTC",
				      "participant": {
				        "user_id": "usr123",
				        "user_name": "name123",
				        "email": "email@email.com",
				        "id": "id123",
				        "registrant_id": "reg123",
				        "date_time": "2026-05-22T16:29:26Z",
				        "public_ip": "1.2.3.4"
				      }
				    }
				  }
				}
				""";

		assertNotNull(springObjectMapper, "Spring ObjectMapper bean should be autowired");
		ZoomPayloadDto dto = springObjectMapper.readValue(json, ZoomPayloadDto.class);
		assertNotNull(dto);
		assertNotNull(dto.payload());
		assertNotNull(dto.payload().object());
		assertNotNull(dto.payload().object().participant());
	}

}
