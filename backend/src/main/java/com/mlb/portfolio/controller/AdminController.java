package com.mlb.portfolio.controller;
import com.mlb.portfolio.dto.*;
import com.mlb.portfolio.model.ContactMessage.InboxStatus;
import com.mlb.portfolio.service.AdminContentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminContentService service;
    public AdminController(AdminContentService service){this.service=service;}
    @GetMapping("/dashboard") public Map<String,Long> dashboard(){return service.dashboard();}
    @GetMapping("/projects") public Page<AdminProjectResponse> projects(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(50) int size){return service.projects(page,size);}
    @GetMapping("/projects/{id}") public AdminProjectResponse project(@PathVariable String id){return service.project(id);}
    @PostMapping("/projects") @ResponseStatus(HttpStatus.CREATED) public AdminProjectResponse addProject(@Valid @RequestBody AdminProjectRequest dto){return service.saveProject(null,dto);}
    @PutMapping("/projects/{id}") public AdminProjectResponse updateProject(@PathVariable String id,@Valid @RequestBody AdminProjectRequest dto){return service.saveProject(id,dto);}
    public record ProjectPatch(Boolean published,Boolean featured,@Min(0) @Max(100000) Integer sortOrder){}
    @PatchMapping("/projects/{id}") public AdminProjectResponse patchProject(@PathVariable String id,@Valid @RequestBody ProjectPatch patch){return service.patchProject(id,patch.published(),patch.featured(),patch.sortOrder());}
    public record ReorderRequest(@Size(min=1,max=100) List<String> ids){}
    @PatchMapping("/projects/order") public Map<String,String> reorder(@Valid @RequestBody ReorderRequest request){service.reorder(request.ids());return Map.of("status","reordered");}
    @DeleteMapping("/projects/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteProject(@PathVariable String id){service.deleteProject(id);}
    @GetMapping("/blog") public Page<AdminBlogResponse> blogs(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(50) int size){return service.blogs(page,size);}
    @GetMapping("/blog/{id}") public AdminBlogResponse blog(@PathVariable String id){return service.blog(id);}
    @PostMapping("/blog") @ResponseStatus(HttpStatus.CREATED) public AdminBlogResponse addBlog(@Valid @RequestBody AdminBlogRequest dto){return service.saveBlog(null,dto);}
    @PutMapping("/blog/{id}") public AdminBlogResponse updateBlog(@PathVariable String id,@Valid @RequestBody AdminBlogRequest dto){return service.saveBlog(id,dto);}
    public record BlogPatch(@NotNull Boolean published){}
    @PatchMapping("/blog/{id}") public AdminBlogResponse patchBlog(@PathVariable String id,@Valid @RequestBody BlogPatch patch){return service.patchBlog(id,patch.published());}
    @DeleteMapping("/blog/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteBlog(@PathVariable String id){service.deleteBlog(id);}
    @GetMapping("/messages") public Page<AdminMessageResponse> messages(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(50) int size){return service.messages(page,size);}
    public record MessagePatch(@NotNull InboxStatus inboxStatus){}
    @PatchMapping("/messages/{id}") public AdminMessageResponse messageStatus(@PathVariable String id,@Valid @RequestBody MessagePatch patch){return service.messageStatus(id,patch.inboxStatus());}
    @PostMapping("/messages/{id}/retry") public AdminMessageResponse retry(@PathVariable String id){return service.retry(id);}
    @DeleteMapping("/messages/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteMessage(@PathVariable String id){service.deleteMessage(id);}
    @GetMapping("/profile") public Map<String,Object> profile(){return service.profileView();}
    @PutMapping("/profile") public Map<String,Object> updateProfile(@Valid @RequestBody AdminProfileRequest profile){return service.updateProfile(profile);}
}
