/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.kogito.serverless.workflow;

import java.util.Iterator;
import java.util.Map.Entry;

import org.kie.kogito.internal.process.runtime.KogitoProcessContext;
import org.kie.kogito.jackson.utils.ObjectMapperFactory;
import org.kie.kogito.process.expr.Expression;
import org.kie.kogito.process.expr.ExpressionHandlerFactory;
import org.kie.kogito.process.expr.ExpressionWorkItemResolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

public class JsonNodeResolver extends ExpressionWorkItemResolver {

    public JsonNodeResolver(String exprLang, String jsonPathExpr, String paramName) {
        super(exprLang, jsonPathExpr, paramName);
    }

    private JsonNode parse(String exprStr) {
        if (ExpressionHandlerFactory.get(language, exprStr).isValid()) {
            return TextNode.valueOf(exprStr);
        } else {
            ObjectMapper objectMapper = ObjectMapperFactory.get();
            try {
                return objectMapper.readTree(exprStr);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Failed to parse input model from ordinary String to Json tree", e);
            }
        }
    }

    @Override
    protected Object evalExpression(Object inputModel, KogitoProcessContext context) {
        return processInputModel(inputModel, parse(expression), context);
    }

    private JsonNode processInputModel(final Object inputModel, final JsonNode expression, KogitoProcessContext context) {
        if (expression.isArray()) {
            final JsonNode processedDefinition = expression.deepCopy();
            for (int index = 0; index < processedDefinition.size(); index++) {
                ((ArrayNode) processedDefinition).set(index, this.processInputModel(inputModel, processedDefinition.get(index), context));
            }
            return processedDefinition;
        } else if (expression.isValueNode()) {
            final String jsonPathExpr = expression.asText();
            Expression evalExpr = ExpressionHandlerFactory.get(language, jsonPathExpr);
            if (evalExpr.isValid()) {
                return evalExpr.eval(inputModel, JsonNode.class, context);
            }
            return expression.deepCopy();
        }

        final JsonNode processedDefinition = expression.deepCopy();
        final Iterator<Entry<String, JsonNode>> fields = processedDefinition.fields();
        while (fields.hasNext()) {
            final Entry<String, JsonNode> jsonField = fields.next();
            ((ObjectNode) processedDefinition).replace(jsonField.getKey(), this.processInputModel(inputModel, jsonField.getValue(), context));
        }
        return processedDefinition;
    }

}
