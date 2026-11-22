package com.assay;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** API-contract paths for builder operations. */
@RestController
@RequestMapping("/api")
class BuilderController {
  private final FormController forms;
  BuilderController(FormController forms) { this.forms = forms; }

  @PutMapping("/sections/{id}") Map<String,Object> editSection(@PathVariable long id,@RequestBody Map<String,String>b,Authentication a){return forms.editSection(id,b,a);}
  @DeleteMapping("/sections/{id}") void delSection(@PathVariable long id,Authentication a){forms.delSection(id,a);}
  @PostMapping("/sections/{sectionId}/dimensions") @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED) Map<String,Object> addDimension(@PathVariable long sectionId,@RequestBody Map<String,String>b,Authentication a){return forms.addDim(sectionId,b,a);}
  @PutMapping("/dimensions/{id}") Map<String,Object> editDimension(@PathVariable long id,@RequestBody Map<String,String>b,Authentication a){return forms.editDim(id,b,a);}
  @DeleteMapping("/dimensions/{id}") void delDimension(@PathVariable long id,Authentication a){forms.delDim(id,a);}
  @PostMapping("/sections/{sectionId}/questions") @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED) Map<String,Object> addQuestion(@PathVariable long sectionId,@RequestBody Map<String,Object>b,Authentication a){return forms.addQuestion(sectionId,b,a);}
  @PutMapping("/questions/{id}") Map<String,Object> editQuestion(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){return forms.editQuestion(id,b,a);}
  @PostMapping("/questions/{id}/clone") @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED) Map<String,Object> cloneQuestion(@PathVariable long id,Authentication a){return forms.cloneQuestion(id,a);}
  @DeleteMapping("/questions/{id}") void delQuestion(@PathVariable long id,Authentication a){forms.delQuestion(id,a);}
}
