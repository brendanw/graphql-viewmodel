Jiashu mentioned that Envoy is transitioning to GraphQL, so I wanted to give it a shot in this project.
There were a few minor frustrations with Apollo. Despite fields being specified as non-null in the schema it generates all fields as nullable.

I tried out two other new things in this project: a pure Rx ViewModel pattern and the Coil image loading library.

I would stick with using glide in place of coil for a production application. The coil usage code is
copied from their sample code on how to work with adapters; I am aware the object allocations within onBindViewHolder 
are not ideal.

On both VSCO and Keepsafe we followed an old school MVP pattern where the presenter handles data wrangling via Rx
and the presenter passes the data to a set of functions defined by the view. In BaseBeta we do the same.
This is a great way to get an app off the ground running and provides readability for newcomers. The downside is that 
you do not get a default log of every state change and the ability to recreate precise states by recreating 
the event history.

I am a fan of the Fragmented podcast and have been meaning to try out a unidirectional data covered on the podcast
(https://github.com/kaushikgopal/movies-usf-android). 

For a sample project this pattern is overkill. For a production app, I think the benefit of a full history of state 
and how it was mutated pays off after a few tricky bug reports.