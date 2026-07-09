#!/usr/bin/env bash
#
# test-check-production-scanners.sh — contract tests for production Java scanners.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCAFFOLD="$(cd "${SCRIPT_DIR}/.." && pwd)"
LIB="${SCRIPT_DIR}/lib"
PASS=0
FAIL=0

pass() { echo "  PASS: $*"; PASS=$((PASS + 1)); }
fail() { echo "  FAIL: $*" >&2; FAIL=$((FAIL + 1)); }

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT
JAVA_DIR="${WORK}/backend/demo/src/main/java/com/example/demo"
mkdir -p "${JAVA_DIR}"

echo "==> magic scanner accepts named constants"
cat > "${JAVA_DIR}/OnlyGood.java" <<'EOF'
package com.example.demo;
public class OnlyGood {
    private static final String CLAIM = "user_id";
    public void run() {
        doWork(CLAIM);
    }
    private void doWork(String claim) {}
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-production-magic-values.sh" backend/demo/src/main/java); then
  pass "named constant allowed"
else
  fail "named constant should be allowed"
fi

echo "==> magic scanner rejects inline literals"
rm -f "${JAVA_DIR}/OnlyGood.java"
cat > "${JAVA_DIR}/OnlyBad.java" <<'EOF'
package com.example.demo;
public class OnlyBad {
    public void run() {
        check("user_id");
    }
    private void check(String c) {}
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-production-magic-values.sh" backend/demo/src/main/java 2>/dev/null); then
  fail "inline literal should be rejected"
else
  pass "inline literal rejected"
fi

echo "==> static scanner detects project static methods"
rm -f "${JAVA_DIR}/"*.java
cat > "${JAVA_DIR}/StaticBad.java" <<'EOF'
package com.example.demo;
public class StaticBad {
    private static String helper(String v) {
        return v;
    }
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-production-static-methods.sh" backend/demo/src/main/java 2>/dev/null); then
  fail "static helper should be rejected"
else
  pass "static helper rejected"
fi

echo "==> static scanner accepts constants and main"
rm -f "${JAVA_DIR}/StaticBad.java"
cat > "${JAVA_DIR}/StaticGood.java" <<'EOF'
package com.example.demo;
public class StaticGood {
    private static final String OK = "x";
    public static void main(String[] args) {}
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-production-static-methods.sh" backend/demo/src/main/java); then
  pass "constants and main allowed"
else
  fail "constants and main should be allowed"
fi

echo "==> current-time scanner rejects direct now calls"
rm -f "${JAVA_DIR}/"*.java
cat > "${JAVA_DIR}/TimeBad.java" <<'EOF'
package com.example.demo;
import java.time.LocalDateTime;
public class TimeBad {
    public LocalDateTime run() {
        return LocalDateTime.now();
    }
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-production-current-time.sh" backend/demo/src/main/java 2>/dev/null); then
  fail "direct now() should be rejected"
else
  pass "direct now() rejected"
fi

echo "==> current-time scanner accepts CurrentTime boundary"
rm -f "${JAVA_DIR}/TimeBad.java"
cat > "${JAVA_DIR}/TimeGood.java" <<'EOF'
package com.example.demo;
import java.time.LocalDateTime;
public class TimeGood {
    private final CurrentTime currentTime;
    public TimeGood(CurrentTime currentTime) {
        this.currentTime = currentTime;
    }
    public LocalDateTime run() {
        return currentTime.nowLocalDateTime();
    }
    interface CurrentTime {
        LocalDateTime nowLocalDateTime();
    }
}
EOF
cat > "${JAVA_DIR}/CurrentTimeImpl.java" <<'EOF'
package com.example.demo;
import java.time.Instant;
public class CurrentTimeImpl {
    public Instant nowInstant() {
        return Instant.now();
    }
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-production-current-time.sh" backend/demo/src/main/java); then
  pass "CurrentTime boundary allowed"
else
  fail "CurrentTime boundary should be allowed"
fi

echo "==> manual-mapping scanner rejects entity setter chains"
rm -f "${JAVA_DIR}/"*.java
cat > "${JAVA_DIR}/ManualMappingBad.java" <<'EOF'
package com.example.demo;
public class ManualMappingBad {
    public CaseStudyEntity create(CaseStudyModel model) {
        CaseStudyEntity entity = new CaseStudyEntity();
        entity.setTitle(model.title());
        entity.setStatus("SUBMITTED");
        return entity;
    }
    record CaseStudyModel(String title) {}
    static class CaseStudyEntity {
        void setTitle(String title) {}
        void setStatus(String status) {}
    }
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-production-manual-mapping.sh" backend/demo/src/main/java 2>/dev/null); then
  fail "entity setter chain should be rejected"
else
  pass "entity setter chain rejected"
fi

echo "==> manual-mapping scanner accepts MapStruct boundary plus technical timestamp"
rm -f "${JAVA_DIR}/ManualMappingBad.java"
cat > "${JAVA_DIR}/ManualMappingGood.java" <<'EOF'
package com.example.demo;
import java.time.LocalDateTime;
public class ManualMappingGood {
    private final CaseStudyMapper mapper;
    private final CurrentTime currentTime;
    public ManualMappingGood(CaseStudyMapper mapper, CurrentTime currentTime) {
        this.mapper = mapper;
        this.currentTime = currentTime;
    }
    public CaseStudyEntity create(CaseStudyModel model) {
        CaseStudyEntity entity = mapper.toEntity(model);
        entity.setCreatedAt(currentTime.nowLocalDateTime());
        return entity;
    }
    record CaseStudyModel(String title) {}
    interface CaseStudyMapper {
        CaseStudyEntity toEntity(CaseStudyModel model);
    }
    interface CurrentTime {
        LocalDateTime nowLocalDateTime();
    }
    static class CaseStudyEntity {
        void setCreatedAt(LocalDateTime createdAt) {}
    }
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-production-manual-mapping.sh" backend/demo/src/main/java); then
  pass "MapStruct boundary allowed"
else
  fail "MapStruct boundary should be allowed"
fi

OPENAPI_DIR="${WORK}/backend/application/src/main/resources/api/v1/specs"
FRONTEND_SCHEMA_DIR="${WORK}/frontend/src/shared/api/generated"
mkdir -p "${OPENAPI_DIR}" "${FRONTEND_SCHEMA_DIR}"

openapi_base() {
  cat > "${OPENAPI_DIR}/openapi.yaml" <<'EOF'
openapi: 3.0.3
info:
  title: Demo
  version: 1.0.0
paths: {}
components:
  schemas:
EOF
}

echo "==> OpenAPI strict scanner accepts closed DTOs and typed dynamic fields"
openapi_base
cat >> "${OPENAPI_DIR}/openapi.yaml" <<'EOF'
    DemoResponseV1:
      type: object
      additionalProperties: false
      required: [id, flags]
      properties:
        id:
          type: integer
          format: int64
        flags:
          type: object
          additionalProperties:
            type: boolean
EOF
: > "${FRONTEND_SCHEMA_DIR}/schema.d.ts"
if (cd "${WORK}" && bash "${LIB}/check-openapi-strict-schemas.sh" backend/application/src/main/resources/api/v1/specs/openapi.yaml frontend/src/shared/api/generated/schema.d.ts); then
  pass "closed OpenAPI DTO allowed"
else
  fail "closed OpenAPI DTO should be allowed"
fi

echo "==> OpenAPI strict scanner rejects object DTOs without explicit closure"
openapi_base
cat >> "${OPENAPI_DIR}/openapi.yaml" <<'EOF'
    MissingClosureResponseV1:
      type: object
      properties:
        id:
          type: integer
          format: int64
EOF
if (cd "${WORK}" && bash "${LIB}/check-openapi-strict-schemas.sh" backend/application/src/main/resources/api/v1/specs/openapi.yaml frontend/src/shared/api/generated/schema.d.ts 2>/dev/null); then
  fail "object DTO without additionalProperties: false should be rejected"
else
  pass "object DTO without explicit closure rejected"
fi

echo "==> OpenAPI strict scanner rejects loose top-level DTOs"
openapi_base
cat >> "${OPENAPI_DIR}/openapi.yaml" <<'EOF'
    LooseResponseV1:
      type: object
      additionalProperties: true
EOF
if (cd "${WORK}" && bash "${LIB}/check-openapi-strict-schemas.sh" backend/application/src/main/resources/api/v1/specs/openapi.yaml frontend/src/shared/api/generated/schema.d.ts 2>/dev/null); then
  fail "loose top-level DTO should be rejected"
else
  pass "loose top-level DTO rejected"
fi

echo "==> OpenAPI strict scanner allows named dynamic helper schemas"
openapi_base
cat >> "${OPENAPI_DIR}/openapi.yaml" <<'EOF'
    JsonMetadataV1:
      type: object
      additionalProperties: true
EOF
cat > "${FRONTEND_SCHEMA_DIR}/schema.d.ts" <<'EOF'
export interface components {
  schemas: {
    JsonMetadataV1: {
      [key: string]: unknown;
    };
  };
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-openapi-strict-schemas.sh" backend/application/src/main/resources/api/v1/specs/openapi.yaml frontend/src/shared/api/generated/schema.d.ts); then
  pass "named dynamic helper schema allowed"
else
  fail "named dynamic helper schema should be allowed"
fi

echo "==> OpenAPI strict scanner rejects generated unknown index signatures for business DTOs"
openapi_base
cat >> "${OPENAPI_DIR}/openapi.yaml" <<'EOF'
    ClosedResponseV1:
      type: object
      additionalProperties: false
      properties:
        id:
          type: integer
          format: int64
EOF
cat > "${FRONTEND_SCHEMA_DIR}/schema.d.ts" <<'EOF'
export interface components {
  schemas: {
    ClosedResponseV1: {
      id?: number;
      [key: string]: unknown;
    };
  };
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-openapi-strict-schemas.sh" backend/application/src/main/resources/api/v1/specs/openapi.yaml frontend/src/shared/api/generated/schema.d.ts 2>/dev/null); then
  fail "business DTO unknown index signature should be rejected"
else
  pass "business DTO unknown index signature rejected"
fi


echo "==> OpenAPI enum scanner accepts standalone versioned enum schemas"
openapi_base
cat >> "${OPENAPI_DIR}/openapi.yaml" <<'EOF'
    UserRoleCodeV1:
      type: string
      description: User role code.
      enum: [admin, teamlead, member]
    UserPermissionSnapshotV1:
      type: object
      additionalProperties: false
      required: [roleCode]
      properties:
        roleCode:
          description: Assigned user role code.
          $ref: '#/components/schemas/UserRoleCodeV1'
EOF
if (cd "${WORK}" && bash "${LIB}/check-openapi-enums.sh" backend/application/src/main/resources/api/v1/specs/openapi.yaml); then
  pass "standalone versioned enum schema allowed"
else
  fail "standalone versioned enum schema should be allowed"
fi

echo "==> OpenAPI enum scanner rejects inline property enums"
openapi_base
cat >> "${OPENAPI_DIR}/openapi.yaml" <<'EOF'
    UserPermissionSnapshotV1:
      type: object
      additionalProperties: false
      properties:
        roleCode:
          type: string
          enum: [admin, teamlead, member]
EOF
if (cd "${WORK}" && bash "${LIB}/check-openapi-enums.sh" backend/application/src/main/resources/api/v1/specs/openapi.yaml 2>/dev/null); then
  fail "inline property enum should be rejected"
else
  pass "inline property enum rejected"
fi

echo "==> OpenAPI enum scanner rejects inline query parameter enums"
openapi_base
cat > "${OPENAPI_DIR}/openapi.yaml" <<'EOF'
openapi: 3.0.3
info:
  title: Demo
  version: 1.0.0
paths:
  /users:
    get:
      parameters:
        - in: query
          name: roleCode
          schema:
            type: string
            enum: [admin, member]
      responses:
        '200':
          description: OK
components:
  schemas: {}
EOF
if (cd "${WORK}" && bash "${LIB}/check-openapi-enums.sh" backend/application/src/main/resources/api/v1/specs/openapi.yaml 2>/dev/null); then
  fail "inline query parameter enum should be rejected"
else
  pass "inline query parameter enum rejected"
fi

echo "==> OpenAPI enum scanner rejects unversioned enum schemas"
openapi_base
cat >> "${OPENAPI_DIR}/openapi.yaml" <<'EOF'
    UserRoleCode:
      type: string
      description: User role code.
      enum: [admin, teamlead, member]
EOF
if (cd "${WORK}" && bash "${LIB}/check-openapi-enums.sh" backend/application/src/main/resources/api/v1/specs/openapi.yaml 2>/dev/null); then
  fail "unversioned enum schema should be rejected"
else
  pass "unversioned enum schema rejected"
fi


SERVICE_DIR="${WORK}/backend/service/src/main/java/com/example/demo/service/widget"
mkdir -p "${SERVICE_DIR}/services/impl" "${SERVICE_DIR}/services"

echo "==> service quality scanner accepts documented contract and small impl"
cat > "${SERVICE_DIR}/services/WidgetService.java" <<'EOF'
package com.example.demo.service.widget.services;
/**
 * Coordinates widget business operations.
 */
public interface WidgetService {
    /**
     * Finds a widget by id.
     *
     * @param id widget identifier
     * @return matching widget
     */
    String findById(Long id);
}
EOF
cat > "${SERVICE_DIR}/services/impl/WidgetServiceImpl.java" <<'EOF'
package com.example.demo.service.widget.services.impl;
public class WidgetServiceImpl {
    private final String repo;
    public WidgetServiceImpl(String repo) {
        this.repo = repo;
    }
    public String findById(Long id) {
        return repo + id;
    }
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-service-contract-quality.sh" backend/service/src/main/java); then
  pass "documented small service allowed"
else
  fail "documented small service should be allowed"
fi

echo "==> service quality scanner rejects missing service JavaDoc"
cat > "${SERVICE_DIR}/services/UndocumentedService.java" <<'EOF'
package com.example.demo.service.widget.services;
public interface UndocumentedService {
    String findById(Long id);
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-service-contract-quality.sh" backend/service/src/main/java 2>/dev/null); then
  fail "undocumented service contract should be rejected"
else
  pass "undocumented service contract rejected"
fi
rm -f "${SERVICE_DIR}/services/UndocumentedService.java"

echo "==> service quality scanner rejects private-method piles"
cat > "${SERVICE_DIR}/services/impl/BulkyServiceImpl.java" <<'EOF'
package com.example.demo.service.widget.services.impl;
public class BulkyServiceImpl {
    public String run() { return one(); }
    private String one() { return "1"; }
    private String two() { return "2"; }
    private String three() { return "3"; }
    private String four() { return "4"; }
    private String five() { return "5"; }
    private String six() { return "6"; }
    private String seven() { return "7"; }
    private String eight() { return "8"; }
    private String nine() { return "9"; }
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-service-contract-quality.sh" backend/service/src/main/java 2>/dev/null); then
  fail "private-method pile should be rejected"
else
  pass "private-method pile rejected"
fi
rm -f "${SERVICE_DIR}/services/impl/BulkyServiceImpl.java"

DOC_OPENAPI="${WORK}/backend/application/src/main/resources/api/v1/specs/documented-openapi.yaml"
UNDOC_OPENAPI="${WORK}/backend/application/src/main/resources/api/v1/specs/undocumented-openapi.yaml"

echo "==> OpenAPI documentation scanner accepts described contract"
cat > "${DOC_OPENAPI}" <<'EOF'
openapi: 3.0.3
info:
  title: Demo
  version: 1.0.0
paths:
  /api/v1/widgets:
    get:
      operationId: listWidgets
      summary: List widgets.
      description: Returns widgets visible to the caller.
      responses:
        "200":
          description: Visible widgets.
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/WidgetListV1"
components:
  schemas:
    WidgetListV1:
      type: object
      additionalProperties: false
      description: List response for widgets.
      properties:
        items:
          type: array
          description: Widgets returned for the current page.
          items:
            $ref: "#/components/schemas/WidgetV1"
    WidgetV1:
      type: object
      additionalProperties: false
      description: Widget visible in the API.
      properties:
        id: { type: integer, format: int64, description: "Widget identifier." }
        name: { type: string, description: "Widget display name." }
EOF
if (cd "${WORK}" && bash "${LIB}/check-openapi-documentation.sh" backend/application/src/main/resources/api/v1/specs/documented-openapi.yaml); then
  pass "documented OpenAPI allowed"
else
  fail "documented OpenAPI should be allowed"
fi

echo "==> OpenAPI documentation scanner rejects missing descriptions"
cat > "${UNDOC_OPENAPI}" <<'EOF'
openapi: 3.0.3
info:
  title: Demo
  version: 1.0.0
paths:
  /api/v1/widgets:
    get:
      operationId: listWidgets
      responses:
        "200":
          description: Visible widgets.
components:
  schemas:
    WidgetV1:
      type: object
      additionalProperties: false
      properties:
        id: { type: integer, format: int64 }
EOF
if (cd "${WORK}" && bash "${LIB}/check-openapi-documentation.sh" backend/application/src/main/resources/api/v1/specs/undocumented-openapi.yaml 2>/dev/null); then
  fail "undocumented OpenAPI should be rejected"
else
  pass "undocumented OpenAPI rejected"
fi


STRUCTURE_WORK="${WORK}/structure-lint-mappers"
mkdir -p "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/mappers" \
  "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/mappers/lesson" \
  "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/web" \
  "${STRUCTURE_WORK}/backend/service/src/main/java/com/aidigital/demo/service/mappers/lesson"
cat > "${STRUCTURE_WORK}/backend/pom.xml" <<'EOF'
<project>
  <groupId>com.aidigital.demo</groupId>
  <packaging>pom</packaging>
  <modules>
    <module>application</module>
    <module>service</module>
    <module>domain</module>
    <module>db</module>
    <module>event-logging-to-db-feature</module>
  </modules>
</project>
EOF
for m in application service domain db event-logging-to-db-feature; do
  mkdir -p "${STRUCTURE_WORK}/backend/${m}/src/main/java"
  cat > "${STRUCTURE_WORK}/backend/${m}/pom.xml" <<EOF
<project><artifactId>${m}</artifactId></project>
EOF
done
cat > "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/web/SpaFallbackController.java" <<'EOF'
package com.aidigital.demo.web;
public class SpaFallbackController {}
EOF
cat > "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/mappers/lesson/LessonApiMapper.java" <<'EOF'
package com.aidigital.demo.mappers.lesson;
import org.mapstruct.Mapper;
@Mapper
public interface LessonApiMapper {}
EOF
cat > "${STRUCTURE_WORK}/backend/service/src/main/java/com/aidigital/demo/service/mappers/lesson/LessonMapper.java" <<'EOF'
package com.aidigital.demo.service.mappers.lesson;
import org.mapstruct.Mapper;
@Mapper
public interface LessonMapper {}
EOF

echo "==> structure-lint accepts aggregate-owned MapStruct mappers"
if (cd "${STRUCTURE_WORK}" && STRUCTURE_LINT_ALLOW_SAMPLE=1 bash "${SCAFFOLD}/scripts/structure-lint.sh"); then
  pass "aggregate-owned mappers allowed"
else
  fail "aggregate-owned mappers should be allowed"
fi

echo "==> structure-lint rejects controller generated DTO construction"
mkdir -p "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/controllers"
cat > "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/controllers/ManualDtoController.java" <<'EOF'
package com.aidigital.demo.controllers;
public class ManualDtoController {
    Object run() {
        return new OkResponseV1();
    }
    static final class OkResponseV1 {}
}
EOF
if (cd "${STRUCTURE_WORK}" && STRUCTURE_LINT_ALLOW_SAMPLE=1 bash "${SCAFFOLD}/scripts/structure-lint.sh" 2>/dev/null); then
  fail "controller generated DTO construction should be rejected"
else
  pass "controller generated DTO construction rejected"
fi
rm -f "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/controllers/ManualDtoController.java"

echo "==> structure-lint rejects Map object service contracts"
mkdir -p "${STRUCTURE_WORK}/backend/service/src/main/java/com/aidigital/demo/service/lesson/services"
cat > "${STRUCTURE_WORK}/backend/service/src/main/java/com/aidigital/demo/service/lesson/services/LessonRevisionGenerationService.java" <<'EOF'
package com.aidigital.demo.service.lesson.services;
import java.util.Map;
public interface LessonRevisionGenerationService {
    Map<String, Object> generateRevisionBrief(Map<String, Object> prompt);
}
EOF
if (cd "${STRUCTURE_WORK}" && STRUCTURE_LINT_ALLOW_SAMPLE=1 bash "${SCAFFOLD}/scripts/structure-lint.sh" 2>/dev/null); then
  fail "Map<String,Object> service contract should be rejected"
else
  pass "Map<String,Object> service contract rejected"
fi
rm -rf "${STRUCTURE_WORK}/backend/service/src/main/java/com/aidigital/demo/service/lesson"

echo "==> structure-lint rejects one-field service ListRecord wrappers"
mkdir -p "${STRUCTURE_WORK}/backend/service/src/main/java/com/aidigital/demo/service/lesson/models"
cat > "${STRUCTURE_WORK}/backend/service/src/main/java/com/aidigital/demo/service/lesson/models/LessonsListRecord.java" <<'EOF'
package com.aidigital.demo.service.lesson.models;
import java.util.List;
public record LessonsListRecord(List<String> lessons) { }
EOF
if (cd "${STRUCTURE_WORK}" && STRUCTURE_LINT_ALLOW_SAMPLE=1 bash "${SCAFFOLD}/scripts/structure-lint.sh" 2>/dev/null); then
  fail "one-field service ListRecord should be rejected"
else
  pass "one-field service ListRecord rejected"
fi
rm -rf "${STRUCTURE_WORK}/backend/service/src/main/java/com/aidigital/demo/service/lesson"

echo "==> structure-lint rejects global ApiDtoMapper"
cat > "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/mappers/ApiDtoMapper.java" <<'EOF'
package com.aidigital.demo.mappers;
public class ApiDtoMapper {}
EOF
if (cd "${STRUCTURE_WORK}" && STRUCTURE_LINT_ALLOW_SAMPLE=1 bash "${SCAFFOLD}/scripts/structure-lint.sh" 2>/dev/null); then
  fail "global ApiDtoMapper should be rejected"
else
  pass "global ApiDtoMapper rejected"
fi
rm -f "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/mappers/ApiDtoMapper.java"


echo "==> structure-lint rejects aggregate-local mapper package"
mkdir -p "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/lesson/mappers"
cat > "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/lesson/mappers/OldLessonApiMapper.java" <<'EOF'
package com.aidigital.demo.lesson.mappers;
import org.mapstruct.Mapper;
@Mapper
public interface OldLessonApiMapper {}
EOF
if (cd "${STRUCTURE_WORK}" && STRUCTURE_LINT_ALLOW_SAMPLE=1 bash "${SCAFFOLD}/scripts/structure-lint.sh" 2>/dev/null); then
  fail "aggregate-local mapper package should be rejected"
else
  pass "aggregate-local mapper package rejected"
fi
rm -rf "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/lesson"

echo "==> structure-lint rejects hand-written application mapper class"
cat > "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/mappers/lesson/ManualMapper.java" <<'EOF'
package com.aidigital.demo.mappers.lesson;
public class ManualMapper {}
EOF
if (cd "${STRUCTURE_WORK}" && STRUCTURE_LINT_ALLOW_SAMPLE=1 bash "${SCAFFOLD}/scripts/structure-lint.sh" 2>/dev/null); then
  fail "hand-written mapper class should be rejected"
else
  pass "hand-written mapper class rejected"
fi
rm -f "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/mappers/lesson/ManualMapper.java"


echo "==> structure-lint rejects manual default Map API mapper"
cat > "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/mappers/lesson/ManualMapApiMapper.java" <<'EOF'
package com.aidigital.demo.mappers.lesson;
import java.util.Map;
import org.mapstruct.Mapper;
@Mapper
public interface ManualMapApiMapper {
    default Object toDto(Map<String, Object> map) { return new WidgetV1(); }
    final class WidgetV1 {}
}
EOF
if (cd "${STRUCTURE_WORK}" && STRUCTURE_LINT_ALLOW_SAMPLE=1 bash "${SCAFFOLD}/scripts/structure-lint.sh" 2>/dev/null); then
  fail "manual default Map API mapper should be rejected"
else
  pass "manual default Map API mapper rejected"
fi
rm -f "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/mappers/lesson/ManualMapApiMapper.java"

echo "==> structure-lint rejects artificial mapper source wrappers"
cat > "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/mappers/lesson/LessonsListSource.java" <<'EOF'
package com.aidigital.demo.mappers.lesson;
import java.util.List;
public record LessonsListSource(List<String> lessons) { }
EOF
if (cd "${STRUCTURE_WORK}" && STRUCTURE_LINT_ALLOW_SAMPLE=1 bash "${SCAFFOLD}/scripts/structure-lint.sh" 2>/dev/null); then
  fail "mapper source wrapper should be rejected"
else
  pass "mapper source wrapper rejected"
fi
rm -f "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/mappers/lesson/LessonsListSource.java"

echo "==> structure-lint rejects creating mapper source wrappers"
cat > "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/mappers/lesson/WrapperApiMapper.java" <<'EOF'
package com.aidigital.demo.mappers.lesson;
import org.mapstruct.Mapper;
@Mapper
public interface WrapperApiMapper {
    default Object wrap(java.util.List<String> lessons) {
        return new LessonsListSource(lessons);
    }
}
EOF
if (cd "${STRUCTURE_WORK}" && STRUCTURE_LINT_ALLOW_SAMPLE=1 bash "${SCAFFOLD}/scripts/structure-lint.sh" 2>/dev/null); then
  fail "mapper-created source wrapper should be rejected"
else
  pass "mapper-created source wrapper rejected"
fi
rm -f "${STRUCTURE_WORK}/backend/application/src/main/java/com/aidigital/demo/mappers/lesson/WrapperApiMapper.java"

FRONTEND_WORK="${WORK}/frontend-ui"
FRONTEND_SRC="${FRONTEND_WORK}/frontend/src"
RESET_DIR="${FRONTEND_SRC}/shared/ui/base"
DEMO_DIR="${FRONTEND_SRC}/features/demo"
mkdir -p "${RESET_DIR}" "${DEMO_DIR}"

write_good_frontend_reset() {
  cat > "${RESET_DIR}/reset.css" <<'EOF'
body {
    overflow-wrap: anywhere;
}

button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-inline-size: 0;
    max-inline-size: 100%;
    text-align: center;
}
EOF
}

echo "==> frontend UI scanner rejects raw px units"
write_good_frontend_reset
cat > "${DEMO_DIR}/demo.css" <<'EOF'
.demo {
    padding: 12px;
}
EOF
if (cd "${FRONTEND_WORK}" && bash "${LIB}/check-frontend-ui-rules.sh" frontend/src 2>/dev/null); then
  fail "raw px unit should be rejected"
else
  pass "raw px unit rejected"
fi
rm -f "${DEMO_DIR}/demo.css"

echo "==> frontend UI scanner rejects broken button reset"
cat > "${RESET_DIR}/reset.css" <<'EOF'
body {
    overflow-wrap: anywhere;
}

button {
    display: block;
}
EOF
if (cd "${FRONTEND_WORK}" && bash "${LIB}/check-frontend-ui-rules.sh" frontend/src 2>/dev/null); then
  fail "broken button reset should be rejected"
else
  pass "broken button reset rejected"
fi

echo "==> frontend UI scanner rejects fixed four-column grids without responsive collapse"
write_good_frontend_reset
cat > "${DEMO_DIR}/demo.css" <<'EOF'
.demo-form {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 0.75rem;
}
EOF
if (cd "${FRONTEND_WORK}" && bash "${LIB}/check-frontend-ui-rules.sh" frontend/src 2>/dev/null); then
  fail "fixed four-column grid should be rejected"
else
  pass "fixed four-column grid rejected"
fi
rm -f "${DEMO_DIR}/demo.css"

echo "==> frontend UI scanner rejects unvalidated forms"
write_good_frontend_reset
cat > "${DEMO_DIR}/DemoForm.tsx" <<'EOF'
export function DemoForm() {
    return (
        <form>
            <label htmlFor="name">Name</label>
            <input id="name" />
            <button type="submit">Save</button>
        </form>
    );
}
EOF
if (cd "${FRONTEND_WORK}" && bash "${LIB}/check-frontend-ui-rules.sh" frontend/src 2>/dev/null); then
  fail "unvalidated form should be rejected"
else
  pass "unvalidated form rejected"
fi
rm -f "${DEMO_DIR}/DemoForm.tsx"

echo "==> frontend UI scanner accepts validated rem-based form"
cat > "${DEMO_DIR}/demo.css" <<'EOF'
.demo {
    display: grid;
    gap: 0.75rem;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    min-width: 0;
}

@media (max-width: 48rem) {
    .demo {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }
}
EOF
cat > "${DEMO_DIR}/DemoForm.tsx" <<'EOF'
export function DemoForm() {
    const nameError = "Name is required";
    return (
        <form>
            <label htmlFor="name">Name</label>
            <input
                id="name"
                required
                aria-invalid={Boolean(nameError)}
                aria-describedby="name-error"
            />
            <p id="name-error" role="alert">{nameError}</p>
            <button type="submit">Save</button>
        </form>
    );
}
EOF
if (cd "${FRONTEND_WORK}" && bash "${LIB}/check-frontend-ui-rules.sh" frontend/src); then
  pass "validated rem-based form allowed"
else
  fail "validated rem-based form should be allowed"
fi
rm -f "${DEMO_DIR}/demo.css" "${DEMO_DIR}/DemoForm.tsx"

echo "==> canonical scaffold passes production scanners"
if (cd "${SCAFFOLD}" && bash "${LIB}/check-production-magic-values.sh"); then
  pass "scaffold passes magic scanner"
else
  fail "scaffold must pass magic scanner"
fi

if (cd "${SCAFFOLD}" && bash "${LIB}/check-production-static-methods.sh"); then
  pass "scaffold passes static scanner"
else
  fail "scaffold must pass static scanner"
fi

if (cd "${SCAFFOLD}" && bash "${LIB}/check-production-current-time.sh"); then
  pass "scaffold passes current-time scanner"
else
  fail "scaffold must pass current-time scanner"
fi

if (cd "${SCAFFOLD}" && bash "${LIB}/check-production-manual-mapping.sh"); then
  pass "scaffold passes manual-mapping scanner"
else
  fail "scaffold must pass manual-mapping scanner"
fi

if (cd "${SCAFFOLD}" && bash "${LIB}/check-frontend-ui-rules.sh"); then
  pass "scaffold passes frontend UI scanner"
else
  fail "scaffold must pass frontend UI scanner"
fi

echo ""
echo "==> test-check-production-scanners: ${PASS} passed, ${FAIL} failed"
[ "${FAIL}" -eq 0 ] && exit 0 || exit 1
