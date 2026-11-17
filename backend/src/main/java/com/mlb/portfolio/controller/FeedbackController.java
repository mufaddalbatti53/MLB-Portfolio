package com.mlb.portfolio.controller;

import com.mlb.portfolio.config.ClientIpResolver;
import com.mlb.portfolio.dto.FeedbackRequest;
import com.mlb.portfolio.dto.FeedbackResponse;
import com.mlb.portfolio.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {
    private final FeedbackService service;
    private final ClientIpResolver ips;
    public FeedbackController(FeedbackService service,ClientIpResolver ips){this.service=service;this.ips=ips;}
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    public FeedbackResponse submit(@Valid @RequestBody FeedbackRequest request,HttpServletRequest servlet){
        return service.submit(request,ips.resolve(servlet));
    }
}
