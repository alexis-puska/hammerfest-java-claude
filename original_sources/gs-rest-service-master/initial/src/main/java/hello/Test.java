package hello;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.springframework.util.FileCopyUtils;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseBody;


@RestController
public class Test {

	@GetMapping("/play.html/manage")
    public String manage(
            @RequestParam(value = "$runid", required = false) String runid,
            @RequestParam(value = "options") String options,
            @RequestParam(value = "mode") String mode,
            @RequestParam(value = "mid") String mid,
            @RequestParam(value = "qid") String qid
            
            ){ 
        System.out.println("here");
        //play.html/manage?$runid=null&options=&mode=solo&mid=nJGSxk6k&qid=Au4YYQ4bExCcWuVt
        //light off
		return "gid=20540684&key=cbe93e282a5a03327d72235d3f1dfc5a&families=0,1,7,8,9,11,12,100,101,102,103,104,105,108,109,110,111,112,1000,1030,5000,5001,5002,5003,5004,5005,5006,5007,5008,5009,5010,5011,5012";
		
		// full option
		//return "gid=20540684&key=cbe93e282a5a03327d72235d3f1dfc5a&families=0,1,7,8,9,11,12,100,101,102,103,104,105,106,107,108,109,110,111,112,1000,1030,5000,5001,5002,5003,5004,5005,5006,5007,5008,5009,5010,5011,5012";
		
		//my option	//"gid=20540684&key=cbe93e282a5a03327d72235d3f1dfc5a&families=0,1,2,3,4,10,11,12,100,101,102,103,104,105,106,107,108,109,110,111,112,1121,1000,1001,1002,1003,1004,1005,1006,1007,1008,1009,1010,1011,1012,1013,1014,1015,1016,1017,1018,1023,1025,1026,1030,5000,5001,5002,5003,5004,5005,5006,5007,5008,5009,5010,5011,5012";
    }
	
	
}

//mvnw spring-boot:run
//mvnw clean package
//java -jar target/gs-rest-service-0.1.0.jar
