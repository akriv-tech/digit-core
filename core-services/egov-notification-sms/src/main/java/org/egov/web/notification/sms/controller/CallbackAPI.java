package org.egov.web.notification.sms.controller;


import org.egov.hash.HashService;
import org.egov.web.notification.sms.config.Producer;
import org.egov.web.notification.sms.models.Report;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.regex.*;

@Service
@Controller
@RequestMapping("/smsbounce/callback")
public class CallbackAPI {


    @Autowired
    Producer producer;

    @Autowired
    HashService hashService;

    @Value("${kafka.topics.sms.bounce}")
    private String topic;

    @RequestMapping(method = { RequestMethod.GET, RequestMethod.POST })
    public ResponseEntity postStatus(@RequestParam String userId,
                                     @RequestParam String jobno,
                                     @RequestParam String mobilenumber,
                                     @RequestParam int status,
                                     @RequestParam String DoneTime,
                                     @RequestParam String messagepart,
                                     @RequestParam String sender_name) {

        boolean stat = false;
        if(status<12 && status>=0) {
            stat = true;
        }
        if(!stat) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Status value should be 0 to 11");
        }
        if(!(Pattern.matches("(^77[0-9]{6}$)",mobilenumber))) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Mobile number should be of format: \n (i) 253(have 8 digits with starting number with 77) or \n (ii) have 8 digits starting with 77 or \n (iii) in the format +253-(start with 77 with 8 digits)");
        }
        Report report = new Report();
        report.setJobno(jobno);
        report.setMessagestatus(status);
        report.setDoneTime(DoneTime);
        report.setUsernameHash(hashService.getHashValue(mobilenumber));

        producer.push(topic, report);
        return ResponseEntity.ok().body("Status successfully sent");
    }
}
