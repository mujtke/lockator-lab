#include <pthread.h>
#include <stdlib.h>
#include <assert.h>

// extern void abort(void);

// void reach_error() { assert(0); }


// void ldv_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();}}; return; }


int a = 0;

void Function( void* ignore )
{
    a = 1;

}

int main( void )
{
    pthread_t id;
    pthread_create( &id , NULL, Function , NULL );

    // ldv_assert(a == 1);
    int b = a;

    pthread_join( id , NULL );
}