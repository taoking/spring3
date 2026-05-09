package contracts.catalog

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'Return a stable ProblemDetail when the product is missing'
    request {
        method GET()
        urlPath('/api/catalog/products/UNKNOWN') {
            queryParameters {
                parameter 'slow': 'false'
                parameter 'fail': 'false'
            }
        }
        headers {
            header 'Authorization': 'Basic dXNlcjp1c2VyMTIz'
        }
    }
    response {
        status NOT_FOUND()
        headers {
            header 'Content-Type': value(
                    consumer('application/problem+json'),
                    producer(regex('application/problem\\+json.*'))
            )
            header 'X-Request-Id': value(
                    consumer('00000000-0000-0000-0000-000000000404'),
                    producer(regex('[0-9a-fA-F-]{36}'))
            )
        }
        body(
                type: 'https://spring3.local/problems/product-not-found',
                title: 'Product not found',
                status: 404,
                detail: 'Product not found: UNKNOWN',
                path: '/api/catalog/products/UNKNOWN',
                errorCode: 'CATALOG_PRODUCT_NOT_FOUND',
                requestId: value(
                        consumer('00000000-0000-0000-0000-000000000404'),
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
