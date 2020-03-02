package depotlifecycle.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import depotlifecycle.ErrorResponse;
import depotlifecycle.domain.Release;
import depotlifecycle.domain.ReleaseDetail;
import depotlifecycle.repositories.PartyRepository;
import depotlifecycle.repositories.ReleaseRepository;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.jackson.convert.ObjectToJsonNodeConverter;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Tag(name = "release")
@Validated
@Secured("isAuthenticated()")
@Controller("/api/v2/release")
@RequiredArgsConstructor
public class ReleaseController {
    private static final Logger LOG = LoggerFactory.getLogger(ReleaseController.class);
    private final PartyRepository partyRepository;
    private final ReleaseRepository releaseRepository;
    private final ObjectToJsonNodeConverter objectToJsonNodeConverter;

    @Get(produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "search for a release", description = "Finds Releases for the given the criteria.", method = "GET", operationId = "indexRelease")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "successful search", content = {@Content(array = @ArraySchema(schema = @Schema(implementation = Release.class)))}),
        @ApiResponse(responseCode = "400", description = "an error occurred", content = {@Content(schema = @Schema(implementation = ErrorResponse.class))}),
        @ApiResponse(responseCode = "403", description = "security disallows access"),
        @ApiResponse(responseCode = "501", description = "this feature is not supported by this server"),
        @ApiResponse(responseCode = "503", description = "API is temporarily paused, and not accepting any activity"),
    })
    public HttpResponse index(@Parameter(name = "releaseNumber", description = "the release number to filter to", in = ParameterIn.QUERY, required = false, schema = @Schema(example = "RHAMG000000", maxLength = 16)) String releaseNumber) {
        LOG.info("Received Release Search");
        Optional.of(releaseNumber).ifPresent(LOG::info);

        List<Release> releases = new ArrayList<>();
        if (releaseNumber != null) {
            Optional<Release> release = releaseRepository.findById(releaseNumber);
            release.ifPresent(releases::add);
        }
        else {
            for (Release release : releaseRepository.findAll()) {
                releases.add(release);
            }
        }

        if (releases.isEmpty()) {
            LOG.info("\tRelease Search - 404 - Not Found");
            return HttpResponse.notFound();
        }
        else {
            LOG.info("\tRelease Search - 200 - Found Releases");
            return HttpResponse.ok(releases);
        }
    }

    @Post(produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "create release", description = "Creates a Release for the given criteria.", method = "POST", operationId = "saveRelease")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "successful create"),
        @ApiResponse(responseCode = "400", description = "an error occurred", content = {@Content(schema = @Schema(implementation = ErrorResponse.class))}),
        @ApiResponse(responseCode = "403", description = "security disallows access"),
        @ApiResponse(responseCode = "404", description = "the release depot was not found"),
        @ApiResponse(responseCode = "501", description = "this feature is not supported by this server"),
        @ApiResponse(responseCode = "503", description = "API is temporarily paused, and not accepting any activity"),
    })
    public void create(@RequestBody(description = "Data to use to update the given Release", required = true, content = {@Content(schema = @Schema(implementation = Release.class))}) Release release) {
        LOG.info("Received Release Create");
        objectToJsonNodeConverter.convert(release, JsonNode.class).ifPresent(jsonNode -> LOG.info(jsonNode.toString()));

        if (releaseRepository.existsById(release.getReleaseNumber())) {
            throw new IllegalArgumentException("Redelivery already exists; please update instead.");
        }

        saveParties(release);

        releaseRepository.save(release);
    }

    private void saveParties(Release release) {
        for (ReleaseDetail detail : release.getDetails()) {
            if (detail.getCustomer() != null) {
                detail.setCustomer(partyRepository.saveOrUpdate(detail.getCustomer()));
            }
        }

        if (release.getDepot() != null) {
            release.setDepot(partyRepository.saveOrUpdate(release.getDepot()));
        }

        if (release.getRecipient() != null) {
            release.setRecipient(partyRepository.saveOrUpdate(release.getRecipient()));
        }
    }

    @Put(uri = "/{releaseNumber}", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "update release", description = "Updates an existing Release.", method = "PUT", operationId = "updateRelease")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "successful update"),
        @ApiResponse(responseCode = "400", description = "an error occurred", content = {@Content(schema = @Schema(implementation = ErrorResponse.class))}),
        @ApiResponse(responseCode = "403", description = "security disallows access"),
        @ApiResponse(responseCode = "404", description = "the release was not found"),
        @ApiResponse(responseCode = "501", description = "this feature is not supported by this server"),
        @ApiResponse(responseCode = "503", description = "API is temporarily paused, and not accepting any activity"),
    })
    public void update(@Parameter(description = "name that need to be updated", required = true, in = ParameterIn.PATH, schema = @Schema(example = "RHAMG000000", maxLength = 16)) String releaseNumber,
                       @RequestBody(description = "Data to use to update the given Release", required = true, content = {@Content(schema = @Schema(implementation = Release.class))}) Release release) {
        LOG.info("Received Release Update");
        objectToJsonNodeConverter.convert(release, JsonNode.class).ifPresent(jsonNode -> LOG.info(jsonNode.toString()));

        if (!releaseRepository.existsById(releaseNumber)) {
            throw new IllegalArgumentException("Release does not exist.");
        }

        saveParties(release);

        releaseRepository.update(release);
    }

    @Error(status = HttpStatus.NOT_FOUND)
    public HttpResponse notFound(HttpRequest request) {
        LOG.info("\tError - 404 - Not Found");
        JsonError error = new JsonError("Not Found");

        return HttpResponse.<JsonError>notFound()
            .body(error);
    }

    @Error
    public HttpResponse onSavedFailed(HttpRequest request, Throwable ex) {
        LOG.info("\tError - 400 - Bad Request", ex);
        ErrorResponse error = new ErrorResponse();
        error.setCode("ERR000");
        error.setMessage(ex.getMessage());

        return HttpResponse.badRequest().body(error);
    }
}