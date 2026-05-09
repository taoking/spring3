package contracts.catalog

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'Return a stable ProblemDetail for simulated catalog failures'
    request {
        method GET()
        urlPath('/api/catalog/products/SKU-FAIL') {
            queryParameters {
                parameter 'slow': 'false'
                parameter 'fail': 'true'
            }
        }
        headers {
            header 'Authorization': 'Basic dXNlcjp1c2VyMTIz'
        }
    }
    response {
        status INTERNAL_SERVER_ERROR()
        headers {
            header 'Content-Type': value(
                    consumer('application/problem+json'),
                    producer(regex('application/problem\\+json.*'))
            )
            header 'X-Request-Id': value(
                    consumer('00000000-0000-0000-0000-000000000500'),
                    producer(regex('[0-9a-fA-F-]{36}'))
            )
        }
        body(
                type: 'https://spring3.local/problems/catalog-simulated-failure',
                title: 'Simulated catalog failure',
                status: 500,
                detail: 'Simulated catalog failure for sku: SKU-FAIL',
                path: '/api/catalog/products/SKU-FAIL',
                errorCode: 'CATALOG_SIMULATED_FAILURE',
                requestId: value(
                        consumer('00000000-0000-0000-0000-000000000500'),
                        producer(regex('[0-9a-fA-F-]{36}'))
                ),
                timestamp: value(
                        consumer('2026-05-09T00:00:00Z'),
                        producer(regex('[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:.]+Z'))
                )
        )
        bodyMatchers {
            jsonPath '$.errorCode', byEquality()
            jsonPath '$.requestId', byRegex('[0-9a-fA-F-]{36}')
            jsonPath '$.timestamp', byRegex('[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:.]+Z')
        }
    }
}
